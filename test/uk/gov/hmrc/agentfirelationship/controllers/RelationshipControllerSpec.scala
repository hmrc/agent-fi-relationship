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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
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

  def testGGProxy: OngoingStubbing[Future[String]] =
    when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful "q213")

  override def beforeEach() {
    reset(mockMongoService)
  }

  "RelationshipStoreController" should {
    "return Status: OK for finding an existing relationship" in {
      when(mockMongoService.findRelationships(validTestRelationship))
        .thenReturn(Future successful List(validTestRelationship))

      val response = mockRelationshipStoreController.findRelationship(validTestARN, payeService, validTestNINO)(FakeRequest("GET", ""))

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any())
    }

    "return Status: NOT_FOUND for not finding data" in {
      when(mockMongoService.findRelationships(Relationship(Arn(validTestARN), payeService, validTestNINO))).thenReturn(Future successful List())

      val response = mockRelationshipStoreController.findRelationship(validTestARN, payeService, validTestNINO)(FakeRequest("GET", ""))

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any())
    }

    "return Status: CREATED for creating new record" in {
      testGGProxy
      when(mockMongoService.createRelationship(validTestRelationship)).thenReturn(Future successful (()))
      when(mockMongoService.findAllRelationshipsForAgent(validTestARN))
        .thenReturn(Future successful List(validTestRelationship))
      when(mockMongoService.findRelationships(validTestRelationship))
        .thenReturn(Future successful List())

      val response = mockRelationshipStoreController.createRelationship(validTestARN, payeService, validTestNINO)(FakeRequest("POST", ""))

      status(response) mustBe CREATED
      verify(mockMongoService, times(1)).createRelationship(any())
      verify(mockMongoService, times(1)).findAllRelationshipsForAgent(any())
      verify(mockMongoService, times(1)).findRelationships(any())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record" in {
      testGGProxy
      when(mockMongoService.createRelationship(validTestRelationship)).thenReturn(Future successful (()))
      when(mockMongoService.findAllRelationshipsForAgent(validTestARN))
        .thenReturn(Future successful List(validTestRelationship))
      when(mockMongoService.findRelationships(validTestRelationship))
        .thenReturn(Future successful List(validTestRelationship))

      val response = mockRelationshipStoreController.createRelationship(validTestARN, payeService, validTestNINO)(FakeRequest("POST", ""))

      status(response) mustBe CREATED
      verify(mockMongoService, times(0)).createRelationship(any())
      verify(mockMongoService, times(1)).findAllRelationshipsForAgent(any())
      verify(mockMongoService, times(1)).findRelationships(any())
    }

    "return Status: FORBIDDEN when 2 or more relationships already exists" in {
      testGGProxy
      when(mockMongoService.createRelationship(validTestRelationship)).thenReturn(Future successful (()))
      when(mockMongoService.findAllRelationshipsForAgent(validTestARN))
        .thenReturn(Future successful List(validTestRelationship, validTestRelationship))
      when(mockMongoService.findRelationships(validTestRelationship))
        .thenReturn(Future successful List(validTestRelationship))

      val response = mockRelationshipStoreController.createRelationship(validTestARN, payeService, validTestNINO)(FakeRequest("POST", ""))

      status(response) mustBe FORBIDDEN
      verify(mockMongoService, times(0)).createRelationship(any())
      verify(mockMongoService, times(1)).findAllRelationshipsForAgent(any())
      verify(mockMongoService, times(1)).findRelationships(any())
    }

    "return Status: Ok for deleting a record" in {
      testGGProxy
      when(mockMongoService.deleteRelationship(validTestRelationship)).thenReturn(Future successful true)

      val response = mockRelationshipStoreController.deleteRelationship(validTestARN, payeService, validTestNINO)(FakeRequest("DELETE", ""))

      status(response) mustBe OK
      verify(mockMongoService, times(1)).deleteRelationship(any())
    }

    "return Status: OK for finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(Relationship(Arn(validTestARN), payeService, validTestNINO)))
        .thenReturn(Future successful List(Relationship(Arn(validTestARN), payeService, validTestNINO)))

      val response = mockRelationshipStoreController.payeCheckRelationship(validTestARN, validTestNINO)(FakeRequest("GET", ""))

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any())
    }

    "return Status: NOT_FOUND for not finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(Relationship(Arn(validTestARN), payeService, validTestNINO))).thenReturn(Future successful List())

      val response = mockRelationshipStoreController.payeCheckRelationship(validTestARN, validTestNINO)(FakeRequest("GET", ""))

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any())
    }
  }
}
