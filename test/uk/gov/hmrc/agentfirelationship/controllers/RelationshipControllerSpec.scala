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
import uk.gov.hmrc.agentfirelationship.connectors.GovernmentGatewayProxyConnector
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import scala.concurrent.Future

class RelationshipControllerSpec extends PlaySpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {
  val mockMongoService: RelationshipMongoService = mock[RelationshipMongoService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockGGProxy: GovernmentGatewayProxyConnector = mock[GovernmentGatewayProxyConnector]
  val mockRelationshipStoreController = new RelationshipController(mockGGProxy, mockAuditService, mockMongoService)

  override def beforeEach() {
    reset(mockMongoService, mockAuditService)
  }

  "RelationshipStoreController" should {
    "return Status: OK when successfully finding a relationship" in {
      when(mockMongoService.findRelationships(eqs(Relationship(Arn(validTestArn), testService, validTestNINO)))(any()))
        .thenReturn(Future successful List(Relationship(Arn(validTestArn), testService, validTestNINO)))

      val response = mockRelationshipStoreController.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: NOT_FOUND for not finding data" in {
      when(mockMongoService.findRelationships(eqs(Relationship(Arn("ARN77777"), testService, validTestNINO)))(any()))
        .thenReturn(Future successful List())

      val response = mockRelationshipStoreController.findRelationship("ARN77777", testService, validTestNINO)(fakeRequest)

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: CREATED for creating new record" in {
      when(mockMongoService.createRelationship(eqs(Relationship(Arn(validTestArn), testService, validTestNINO)))(any()))
        .thenReturn(Future successful (()))
      when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful testCredId)

      val response = mockRelationshipStoreController.createRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe CREATED
      verify(mockMongoService, times(1)).createRelationship(any())(any())
    }

    "return Status: OK for deleting a record" in {
      when(mockMongoService.deleteRelationship(eqs(Relationship(Arn(validTestArn), testService, validTestNINO)))(any()))
        .thenReturn(Future successful true)
      when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful testCredId)

      val response = mockRelationshipStoreController.deleteRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).deleteRelationship(any())(any())
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
      when(mockMongoService.findRelationships(eqs(Relationship(Arn(validTestArn), testService, validTestNINO)))(any()))
        .thenReturn(Future successful List(Relationship(Arn(validTestArn), testService, validTestNINO)))

      val response = mockRelationshipStoreController.payeCheckRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: NOT_FOUND for not finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(Relationship(Arn(validTestArn), testService, validTestNINO)))(any()))
        .thenReturn(Future successful List())

      val response = mockRelationshipStoreController.payeCheckRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }
  }
}
