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
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.connectors.{AuthConnector, UserDetails}
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService

import scala.concurrent.Future

class RelationshipControllerSpec extends PlaySpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {
  val mockMongoService: RelationshipMongoService = mock[RelationshipMongoService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val controller = new RelationshipController(mockAuthConnector, mockAuditService, mockMongoService)

  override def afterEach() {
    reset(mockMongoService, mockAuditService, mockAuthConnector)
  }

  "RelationshipController" should {
    "return Status: OK when successfully finding a relationship" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }

    "return Status: NOT_FOUND for not finding data" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())

      val response = controller.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }

    "return Status: CREATED for creating new record" in {
      when(mockAuthConnector.userDetails(any(), any())).thenReturn(Future successful UserDetails(testCredId))
      when(mockAuditService.sendCreateRelationshipEvent(any())(any(), any())).thenReturn(Future successful())

      when(mockMongoService.createRelationship(any())(any())).thenReturn(Future successful (()))
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())

      val response = controller.createRelationship(validTestArn, testService, validTestNINO, testResponseDate)(fakeRequest)

      status(response) mustBe CREATED
      verify(mockMongoService, times(1)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }

    "send an audit event if the relationship is successfully created" in {
      when(mockAuthConnector.userDetails(any(), any())).thenReturn(Future successful UserDetails(testCredId))
      when(mockAuditService.sendCreateRelationshipEvent(any())(any(), any())).thenReturn(Future successful())

      when(mockMongoService.createRelationship(eqs(validTestRelationship))(any())).thenReturn(Future successful (()))
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())

      val response = controller.createRelationship(validTestArn, testService, validTestNINO, testResponseDate)(fakeRequest)
      await(response)
      verify(mockAuditService, times(1)).sendCreateRelationshipEvent(any())(any(), any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record" in {
      when(mockAuthConnector.userDetails(any(), any())).thenReturn(Future successful UserDetails(testCredId))
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.createRelationship(validTestArn, testService, validTestNINO, testResponseDate)(fakeRequest)

      status(response) mustBe CREATED
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }

    "return Status: CREATED when additional relationships are created" in {
      when(mockAuthConnector.userDetails(any(), any())).thenReturn(Future successful UserDetails(testCredId))
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.createRelationship(validTestArn, testService, validTestNINO, testResponseDate)(fakeRequest)

      status(response) mustBe CREATED
      verify(mockMongoService, times(0)).createRelationship(any())(any())
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }

    "return Status: OK for deleting a record" in {
      when(mockMongoService.deleteRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful true)
      when(mockAuthConnector.userDetails(any(), any())).thenReturn(Future successful UserDetails(testCredId))
      when(mockAuditService.sendDeleteRelationshipEvent(any())(any(), any())).thenReturn(Future successful())

      val response = controller.deleteRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).deleteRelationship(any(), any(), any())(any())
    }

    "send an audit event if the relationship is successfully deleting" in {
      when(mockMongoService.deleteRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful true)
      when(mockAuthConnector.userDetails(any(), any())).thenReturn(Future successful UserDetails(testCredId))
      when(mockAuditService.sendDeleteRelationshipEvent(any())(any(), any())).thenReturn(Future successful())

      val response = controller.deleteRelationship(validTestArn, testService, validTestNINO)(fakeRequest)
      await(response)
      verify(mockAuditService, times(1)).sendDeleteRelationshipEvent(any())(any(), any())
    }


    "return Status: NOT_FOUND for failing to delete a record" in {
      when(mockMongoService.deleteRelationship(any(), any(), any())(any()))
        .thenReturn(Future successful false)
      when(mockAuthConnector.userDetails(any(), any())).thenReturn(Future successful UserDetails(testCredId))
      when(mockAuditService.sendDeleteRelationshipEvent(any())(any(), any())).thenReturn(Future successful())

      val response = controller.deleteRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).deleteRelationship(any(), any(), any())(any())
    }

    "return Status: OK for finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.afiCheckRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }

    "return Status: NOT_FOUND for not finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any()))
        .thenReturn(Future successful List())

      val response = controller.afiCheckRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any(), any(), any())(any())
    }
  }
}
