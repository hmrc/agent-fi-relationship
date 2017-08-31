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
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.connectors.GovernmentGatewayProxyConnector
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService

import scala.concurrent.Future

class RelationshipControllerSpec extends PlaySpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {
  val mockMongoService: RelationshipMongoService = mock[RelationshipMongoService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockGGProxy: GovernmentGatewayProxyConnector = mock[GovernmentGatewayProxyConnector]
  val mockRelationshipStoreController = new RelationshipController(mockGGProxy, mockAuditService, mockMongoService)

  def testGGProxy: OngoingStubbing[Future[String]] =
    when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful testCredId)

  override def beforeEach() {
    reset(mockMongoService, mockAuditService)
  }

  "RelationshipController" should {
    "return Status: OK when successfully finding a relationship" in {
      when(mockMongoService.findRelationships(eqs(validTestRelationship))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = mockRelationshipStoreController.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: NOT_FOUND for not finding data" in {
      when(mockMongoService.findRelationships(eqs(validTestRelationship))(any()))
        .thenReturn(Future successful List())

      val response = mockRelationshipStoreController.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: CREATED for creating new record" in {
      testGGProxy
      when(mockMongoService.createRelationship(any())(any())).thenReturn(Future successful (()))
      when(mockMongoService.findAllRelationshipsForAgent(eqs(validTestArn))(any()))
        .thenReturn(Future successful List(validTestRelationship))
      when(mockMongoService.findRelationships(any())(any()))
        .thenReturn(Future successful List())

      val response = mockRelationshipStoreController.createRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe CREATED
      verify(mockMongoService, times(1)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findAllRelationshipsForAgent(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }
    "send an audit event if the relationship is successfully created" in {
      testGGProxy
      when(mockMongoService.createRelationship(any())(any())).thenReturn(Future successful (()))
      when(mockMongoService.findAllRelationshipsForAgent(eqs(validTestArn))(any()))
        .thenReturn(Future successful List(validTestRelationship))
      when(mockMongoService.findRelationships(any())(any()))
        .thenReturn(Future successful List())

      val response = mockRelationshipStoreController.createRelationship(validTestArn, testService, validTestNINO)(fakeRequest)
      await(response)
      verify(mockAuditService, times(1)).sendCreateRelationshipEvent(any())(any(), any())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record" in {
      testGGProxy
      when(mockMongoService.findAllRelationshipsForAgent(eqs(validTestArn))(any()))
        .thenReturn(Future successful List(validTestRelationship))
      when(mockMongoService.findRelationships(any())(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = mockRelationshipStoreController.createRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe CREATED
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findAllRelationshipsForAgent(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: FORBIDDEN when 2 or more relationships already exists" in {
      testGGProxy
      when(mockMongoService.findAllRelationshipsForAgent(eqs(validTestArn))(any()))
        .thenReturn(Future successful List(validTestRelationship, validTestRelationship))
      when(mockMongoService.findRelationships(any())(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = mockRelationshipStoreController.createRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe FORBIDDEN
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findAllRelationshipsForAgent(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: OK for deleting a record" in {
      when(mockMongoService.deleteRelationship(eqs(validTestRelationship))(any()))
        .thenReturn(Future successful true)
      when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful testCredId)

      val response = mockRelationshipStoreController.deleteRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).deleteRelationship(any())(any())
    }

    "send an audit event if the relationship is successfully deleting" in {
      when(mockMongoService.deleteRelationship(eqs(validTestRelationship))(any()))
        .thenReturn(Future successful true)
      when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful testCredId)

      val response = mockRelationshipStoreController.deleteRelationship(validTestArn, testService, validTestNINO)(fakeRequest)
      await(response)
      verify(mockAuditService, times(1)).sendDeleteRelationshipEvent(any())(any(), any())
    }


    "return Status: NOT_FOUND for failing to delete a record" in {
      when(mockMongoService.deleteRelationship(any())(any()))
        .thenReturn(Future successful false)
      when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful testCredId)

      val response = mockRelationshipStoreController.deleteRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).deleteRelationship(any())(any())
    }

    "return Status: OK for finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(validTestRelationship))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = mockRelationshipStoreController.afiCheckRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: NOT_FOUND for not finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(validTestRelationship))(any()))
        .thenReturn(Future successful List())

      val response = mockRelationshipStoreController.afiCheckRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }
  }
}
