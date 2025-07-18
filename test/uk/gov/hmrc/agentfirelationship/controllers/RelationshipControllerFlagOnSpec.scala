/*
 * Copyright 2023 HM Revenue & Customs
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

import java.net.URL

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.mockito.ArgumentMatchers.{ eq => eqs }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.ControllerComponents
import play.api.test.Helpers
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.AuditData
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthConnector
import uk.gov.hmrc.agentfirelationship.connectors.DesConnector
import uk.gov.hmrc.agentfirelationship.models.BasicAuthentication
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentfirelationship.services.CesaRelationshipCopyService
import uk.gov.hmrc.agentfirelationship.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HeaderCarrier

class RelationshipControllerFlagOnSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockMongoService: RelationshipMongoRepository =
    mock[RelationshipMongoRepository]
  val mockAuditService: AuditService           = mock[AuditService]
  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockCesaRelationship: CesaRelationshipCopyService = {
    mock[CesaRelationshipCopyService]
  }
  val mockDesConnector: DesConnector                         = mock[DesConnector]
  val mockEc: ExecutionContext                               = mock[ExecutionContext]
  val mockAgentClientAuthConnector: AgentClientAuthConnector = mock[AgentClientAuthConnector]
  val testAppConfig: AppConfig = new AppConfig {
    override val appName: String                             = "agent-fi-relationship"
    override val agentMappingBaseUrl: URL                    = new URL("http://localhost:9999/agent-mapping")
    override val desBaseUrl: URL                             = new URL("http://localhost:9999/des")
    override val authBaseUrl: URL                            = new URL("http://localhost:9999/auth")
    override val copyCesaRelationshipFlag: Boolean           = true
    override val checkCesaRelationshipFlag: Boolean          = true
    override val desEnvironment: String                      = "des.env"
    override val desAuthToken: String                        = "des.auth.token"
    override val oldStrideRole: String                       = "maintain agent relationships"
    override val newStrideRole: String                       = "maintain_agent_relationships"
    override val terminationStrideRole: String               = "caat"
    override val inactiveRelationshipsShowLastDays: Duration = Duration.create("30 days")
    override def expectedAuth: BasicAuthentication           = BasicAuthentication("username", "password")
  }
  val mockControllerComponents: ControllerComponents = Helpers.stubControllerComponents()
  val oldStrideRole                                  = "maintain agent relationships"
  val newStrideRole                                  = "maintain_agent_relationships"

  override def afterEach(): Unit = {
    reset(mockMongoService)
    reset(mockAuditService)
    reset(mockPlayAuthConnector)
    reset(mockCesaRelationship)
  }

  "RelationshipController (Both Flags On)" should {

    val controller = new RelationshipController(
      mockAuditService,
      mockMongoService,
      mockAgentClientAuthConnector,
      mockCesaRelationship,
      mockDesConnector,
      testAppConfig,
      mockControllerComponents
    )

    "return Status: OK when successfully finding a relationship in Cesa and Agent Mapping" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))
      )
        .thenReturn(Future.successful(List(validTestRelationship)))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: NOT_FOUND if any previous relationships are found" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))
      )
        .thenReturn(Future.successful(List()))

      when(mockMongoService.findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO)))
        .thenReturn(Future.successful(List(validTestRelationshipCesa)))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())

      verify(mockMongoService, times(1))
        .findAnyRelationships(any[String](), any[String](), any[String]())
    }

    "return Status: NOT FOUND when no relationship in Cesa and Agent Mapping is found" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))
      )
        .thenReturn(Future.successful(List()))
      when(mockMongoService.findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO)))
        .thenReturn(Future.successful(List()))
      when(
        mockCesaRelationship
          .lookupCesaForOldRelationship(eqs(Arn(validTestArn)), eqs(Nino(validTestNINO)))(
            any[ExecutionContext](),
            any[HeaderCarrier](),
            any[AuditData]()
          )
      )
        .thenReturn(Future.successful(Set[SaAgentReference]()))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())

      verify(mockMongoService, times(1))
        .findAnyRelationships(any[String](), any[String](), any[String]())

      verify(mockCesaRelationship, times(1))
        .lookupCesaForOldRelationship(any[Arn](), any[Nino]())(
          any[ExecutionContext](),
          any[HeaderCarrier](),
          any[AuditData]()
        )
    }

    "return Status: NOT FOUND when failed to copy relationship from Cesa " in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))
      )
        .thenReturn(Future.successful(List()))
      when(
        mockMongoService
          .findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))
      )
        .thenReturn(Future.successful(List()))
      when(
        mockCesaRelationship
          .lookupCesaForOldRelationship(eqs(Arn(validTestArn)), eqs(Nino(validTestNINO)))(
            any[ExecutionContext](),
            any[HeaderCarrier](),
            any[AuditData]()
          )
      )
        .thenReturn(Future.successful(Set[SaAgentReference]()))

      when(
        mockMongoService
          .createRelationship(any[Relationship]())
      )
        .thenReturn(Future.failed(new Exception("Error")))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())

      verify(mockMongoService, times(1))
        .findAnyRelationships(any[String](), any[String](), any[String]())

      verify(mockCesaRelationship, times(1))
        .lookupCesaForOldRelationship(any[Arn](), any[Nino]())(
          any[ExecutionContext](),
          any[HeaderCarrier](),
          any[AuditData]()
        )
    }

    "return Status: OK when relationship in Cesa and Agent Mapping is found, but active relationships already exist" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))
      )
        .thenReturn(Future.successful(List()))
      when(mockMongoService.findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO)))
        .thenReturn(Future.successful(List()))
      when(
        mockCesaRelationship
          .lookupCesaForOldRelationship(eqs(Arn(validTestArn)), eqs(Nino(validTestNINO)))(
            any[ExecutionContext](),
            any[HeaderCarrier](),
            any[AuditData]()
          )
      )
        .thenReturn(Future.successful(Set(SaAgentReference("existingRef"))))
      when(mockMongoService.findActiveClientRelationships(validTestNINO))
        .thenReturn(Future.successful(List(validTestRelationship)))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())

      verify(mockMongoService, times(1))
        .findAnyRelationships(any[String](), any[String](), any[String]())

      verify(mockCesaRelationship, times(1))
        .lookupCesaForOldRelationship(any[Arn](), any[Nino]())(
          any[ExecutionContext](),
          any[HeaderCarrier](),
          any[AuditData]()
        )

      verify(mockMongoService, times(1))
        .findActiveClientRelationships(any[String]())
    }

    "return Status: OK when relationship in Cesa and Agent Mapping is found, and active relationships don't already exist" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))
      )
        .thenReturn(Future.successful(List()))
      when(mockMongoService.findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO)))
        .thenReturn(Future.successful(List()))
      when(
        mockCesaRelationship
          .lookupCesaForOldRelationship(eqs(Arn(validTestArn)), eqs(Nino(validTestNINO)))(
            any[ExecutionContext](),
            any[HeaderCarrier](),
            any[AuditData]()
          )
      )
        .thenReturn(Future.successful(Set(SaAgentReference("existingRef"))))
      when(mockMongoService.findActiveClientRelationships(validTestNINO))
        .thenReturn(Future.successful(List.empty[Relationship]))
      when(mockMongoService.createRelationship(any[Relationship]))
        .thenReturn(Future.successful(()))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe OK

      verify(mockMongoService, times(2))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())

      verify(mockMongoService, times(1))
        .findAnyRelationships(any[String](), any[String](), any[String]())

      verify(mockCesaRelationship, times(1))
        .lookupCesaForOldRelationship(any[Arn](), any[Nino]())(
          any[ExecutionContext](),
          any[HeaderCarrier](),
          any[AuditData]()
        )

      verify(mockMongoService, times(1))
        .findActiveClientRelationships(any[String]())

      verify(mockMongoService, times(1))
        .createRelationship(any[Relationship]())
    }
  }
}
