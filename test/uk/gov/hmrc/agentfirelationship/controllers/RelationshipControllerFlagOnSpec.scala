/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentfirelationship.controllers

import javax.inject.Provider
import org.mockito.ArgumentMatchers.{any, eq => eqs}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentfirelationship.connectors.{AgentClientAuthConnector, MicroserviceAuthConnector}
import uk.gov.hmrc.agentfirelationship.models.{Relationship, RelationshipStatus}
import uk.gov.hmrc.agentfirelationship.services.{CesaRelationshipCopyService, RelationshipMongoService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.{AuthConnector, PlayAuthConnector}
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class RelationshipControllerFlagOnSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockMongoService: RelationshipMongoService =
    mock[RelationshipMongoService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockCesaRelationship: CesaRelationshipCopyService =
    mock[CesaRelationshipCopyService]
  val mockMicroserviceAuthConnector: MicroserviceAuthConnector =
    mock[MicroserviceAuthConnector]
  val mockEc: ExecutionContext = mock[ExecutionContext]
  val mockAgentClientAuthConnector: AgentClientAuthConnector =
    new AgentClientAuthConnector(mockMicroserviceAuthConnector) {
      override def authConnector: AuthConnector = mockPlayAuthConnector
    }
  val oldStrideRole = "maintain agent relationships"
  val newStrideRole = "maintain_agent_relationships"

  val ecp: Provider[ExecutionContextExecutor] = new Provider[ExecutionContextExecutor] {
    override def get(): ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global
  }
  override def afterEach() {
    reset(mockMongoService, mockAuditService, mockPlayAuthConnector, mockCesaRelationship)
  }

  "RelationshipController (Both Flags On)" should {

    val controller = new RelationshipController(
      mockAuditService,
      mockMongoService,
      mockAgentClientAuthConnector,
      mockCesaRelationship,
      ecp,
      true,
      true,
      oldStrideRole,
      newStrideRole)

    "return Status: OK when successfully finding a relationship in Cesa and Agent Mapping" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationship))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: NOT_FOUND if any previous relationships are found" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List())

      when(
        mockMongoService.findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(
          any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationshipCesa))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findAnyRelationships(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "return Status: NOT FOUND when no relationship in Cesa and Agent Mapping is found" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List())
      when(
        mockMongoService.findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(
          any[ExecutionContext]()))
        .thenReturn(Future successful List())
      when(
        mockCesaRelationship
          .lookupCesaForOldRelationship(eqs(Arn(validTestArn)), eqs(Nino(validTestNINO)))(
            any[ExecutionContext](),
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[AuditData]()))
        .thenReturn(Future successful Set[SaAgentReference]())

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findAnyRelationships(any[String](), any[String](), any[String]())(any[ExecutionContext]())

      verify(mockCesaRelationship, times(1))
        .lookupCesaForOldRelationship(any[Arn](), any[Nino]())(
          any[ExecutionContext](),
          any[HeaderCarrier](),
          any[Request[Any]](),
          any[AuditData]())
    }

    "return Status: NOT FOUND when failed to copy relationship from Cesa " in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List())
      when(
        mockMongoService
          .findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful List())
      when(
        mockCesaRelationship
          .lookupCesaForOldRelationship(eqs(Arn(validTestArn)), eqs(Nino(validTestNINO)))(
            any[ExecutionContext](),
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[AuditData]()))
        .thenReturn(Future successful Set[SaAgentReference]())

      when(
        mockMongoService
          .createRelationship(any[Relationship]())(any[ExecutionContext]()))
        .thenReturn(Future failed new Exception("Error"))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findAnyRelationships(any[String](), any[String](), any[String]())(any[ExecutionContext]())

      verify(mockCesaRelationship, times(1))
        .lookupCesaForOldRelationship(any[Arn](), any[Nino]())(
          any[ExecutionContext](),
          any[HeaderCarrier](),
          any[Request[Any]](),
          any[AuditData]())
    }
  }
}
