/*
 * Copyright 2017 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => eqs}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.connectors.{AgentClientAuthConnector, AuthAuditConnector}
import uk.gov.hmrc.agentfirelationship.services.{CesaRelationshipCopyService, RelationshipMongoService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class RelationshipControllerFlagOnSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {

  val mockMongoService: RelationshipMongoService = mock[RelationshipMongoService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockAuthAuditConnector: AuthAuditConnector = mock[AuthAuditConnector]
  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockCesaRelationship: CesaRelationshipCopyService = mock[CesaRelationshipCopyService]
  val mockAgentClientAuthConnector: AgentClientAuthConnector = new AgentClientAuthConnector {
    override def authConnector: AuthConnector = mockPlayAuthConnector
  }

  override def afterEach() {
    reset(mockMongoService, mockAuditService, mockPlayAuthConnector, mockCesaRelationship)
  }

  private def authStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any(), any())).thenReturn(returnValue)


  "RelationshipController (Both Flags On)" should {

    val controller = new RelationshipController(
      mockAuthAuditConnector,
      mockAuditService,
      mockMongoService,
      mockAgentClientAuthConnector,
      mockCesaRelationship, true, true)

    "return Status: OK when successfully finding a relationship in Cesa and Agent Mapping" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }

    "return Status: OK when successfully finding a relationship in Cesa" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())
      when(mockMongoService.findCeasedRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List(validTestRelationshipCesa))

      val response = controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
      verify(mockMongoService, times(1)).findCeasedRelationships(any(), any(), any())(any())
    }

    "return Status: NOT FOUND when no relationship in Cesa and Agent Mapping is found" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())
      when(mockMongoService.findCeasedRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())
      when(mockCesaRelationship.lookupCesaForOldRelationship(eqs(Arn(validTestArn)), eqs(Nino(validTestNINO)))(any(), any(),any(),any()))
        .thenReturn(Future successful Set[SaAgentReference]())

      val response = controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
      verify(mockMongoService, times(1)).findCeasedRelationships(any(), any(), any())(any())
      verify(mockCesaRelationship, times(1)).lookupCesaForOldRelationship(any(), any())(any(), any(), any(), any())
    }

    "return Status: NOT FOUND when failed to copy relationship from Cesa " in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())
      when(mockMongoService.findCeasedRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())
      when(mockCesaRelationship.lookupCesaForOldRelationship(eqs(Arn(validTestArn)), eqs(Nino(validTestNINO)))(any(), any(),any(),any()))
        .thenReturn(Future successful Set[SaAgentReference]())
      when(mockMongoService.createRelationship(any())(any()))
        .thenReturn(Future failed new Exception("Error"))

      val response = controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
      verify(mockMongoService, times(1)).findCeasedRelationships(any(), any(), any())(any())
      verify(mockCesaRelationship, times(1)).lookupCesaForOldRelationship(any(), any())(any(), any(), any(), any())
    }
  }
}
