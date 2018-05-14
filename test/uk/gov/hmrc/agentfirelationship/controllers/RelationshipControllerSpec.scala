/*
 * Copyright 2018 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{ any, eq => eqs }
import org.mockito.Mockito.{ reset, times, verify, when }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.connectors.{ AgentClientAuthConnector, AuthAuditConnector, MicroserviceAuthConnector, UserDetails }
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.services.{ CesaRelationshipCopyService, RelationshipMongoService }
import uk.gov.hmrc.auth.core.retrieve.{ Retrieval, ~ }
import uk.gov.hmrc.auth.core.{ AffinityGroup, AuthConnector, Enrolments, PlayAuthConnector }
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ ExecutionContext, Future }

class RelationshipControllerSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockMongoService: RelationshipMongoService = mock[RelationshipMongoService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockAuthAuditConnector: AuthAuditConnector = mock[AuthAuditConnector]
  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockCesaRelationship: CesaRelationshipCopyService = mock[CesaRelationshipCopyService]
  val mockMicroserviceAuthConnector: MicroserviceAuthConnector = mock[MicroserviceAuthConnector]
  val mockAgentClientAuthConnector: AgentClientAuthConnector = new AgentClientAuthConnector(mockMicroserviceAuthConnector) {
    override def authConnector: AuthConnector = mockPlayAuthConnector
  }

  val controller = new RelationshipController(
    mockAuthAuditConnector,
    mockAuditService,
    mockMongoService,
    mockAgentClientAuthConnector,
    mockCesaRelationship, false, false)

  override def afterEach() {
    reset(mockMongoService, mockAuditService, mockPlayAuthConnector, mockCesaRelationship)
  }

  private def authStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any(), any())).thenReturn(returnValue)

  "RelationshipController" should {
    "return Status: OK when successfully finding a relationship" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: NOT_FOUND for not finding data" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List())
      when(mockMongoService.findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())

      val response = controller.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: CREATED for creating new record as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockAuditService.sendCreateRelationshipEvent(any())(any(), any())).thenReturn(Future successful (()))

      when(mockMongoService.createRelationship(any())(any())).thenReturn(Future successful (()))
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List())

      val response = controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(1)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: CREATED for creating new record as an agent" in {
      authStub(agentAffinityAndEnrolments)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockAuditService.sendCreateRelationshipEvent(any())(any(), any())).thenReturn(Future successful (()))

      when(mockMongoService.createRelationship(any())(any())).thenReturn(Future successful (()))
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List())

      val response = controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(1)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "send an audit event if the relationship is successfully created" in {
      authStub(agentAffinityAndEnrolments)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockAuditService.sendCreateRelationshipEvent(any())(any(), any())).thenReturn(Future successful (()))

      when(mockMongoService.createRelationship(eqs(validTestRelationship))(any())).thenReturn(Future successful (()))
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List())

      val response = controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)
      await(response)
      verify(mockAuditService, times(1)).sendCreateRelationshipEvent(any())(any(), any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record, as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record, as an agent" in {
      authStub(agentAffinityAndEnrolments)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: CREATED when additional relationships are created as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: CREATED when additional relationships are created as an agent" in {
      authStub(agentAffinityAndEnrolments)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: FORBIDDEN when logged in Agent ARN does not match given ARN when creating relationship" in {
      authStub(agentAffinityAndEnrolments)

      val response = controller.createRelationship("TARN0000001", testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(0)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: FORBIDDEN when logged in Client NINO does not match given NINO when creating relationship" in {
      authStub(clientAffinityAndEnrolments)

      val response = controller.createRelationship(validTestArn, testService, "AB123456C")(fakeCreateRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(0)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: OK for deleting a record as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(mockMongoService.terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful true)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockAuditService.sendDeleteRelationshipEvent(any())(any(), any())).thenReturn(Future successful (()))

      val response = controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1)).terminateRelationship(any(), any(), any())(any())
    }

    "return Status: OK for deleting a record as an agent" in {
      authStub(agentAffinityAndEnrolments)
      when(mockMongoService.terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful true)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockAuditService.sendDeleteRelationshipEvent(any())(any(), any())).thenReturn(Future successful (()))

      val response = controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1)).terminateRelationship(any(), any(), any())(any())
    }

    "send an audit event if the relationship is successfully deleting" in {
      authStub(agentAffinityAndEnrolments)
      when(mockMongoService.terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful true)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockAuditService.sendDeleteRelationshipEvent(any())(any(), any())).thenReturn(Future successful (()))

      val response = controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)
      await(response)
      verify(mockAuditService, times(1)).sendDeleteRelationshipEvent(any())(any(), any())
    }

    "return Status: NOT_FOUND for failing to delete a record as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(mockMongoService.terminateRelationship(any(), any(), any())(any()))
        .thenReturn(Future successful false)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockAuditService.sendDeleteRelationshipEvent(any())(any(), any())).thenReturn(Future successful (()))

      val response = controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1)).terminateRelationship(any(), any(), any())(any())
    }

    "return Status: NOT_FOUND for failing to delete a record as an agent" in {
      authStub(agentAffinityAndEnrolments)
      when(mockMongoService.terminateRelationship(any(), any(), any())(any()))
        .thenReturn(Future successful false)
      when(mockAuthAuditConnector.userDetails(any(), any())).thenReturn(Future successful userDetails)
      when(mockAuditService.sendDeleteRelationshipEvent(any())(any(), any())).thenReturn(Future successful (()))

      val response = controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1)).terminateRelationship(any(), any(), any())(any())
    }

    "return Status: FORBIDDEN when logged in Agent ARN does not match given ARN when deleting relationship" in {
      authStub(agentAffinityAndEnrolments)

      val response = controller.terminateRelationship("TARN0000001", testService, validTestNINO)(fakeRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0)).terminateRelationship(any(), any(), any())(any())
    }

    "return Status: FORBIDDEN when logged in Client NINO does not match given NINO when deleting relationship" in {
      authStub(clientAffinityAndEnrolments)

      val response = controller.terminateRelationship(validTestArn, testService, "AB123456C")(fakeRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0)).terminateRelationship(any(), any(), any())(any())
    }

    "return Status: OK for finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }

    "return Status: NOT_FOUND for not finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))(any()))
        .thenReturn(Future successful List())
      when(mockMongoService.findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())

      val response = controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any(), any())(any())
    }
  }
}
