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

import org.mockito.ArgumentMatchers.{eq => eqs, any}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.connectors.GovernmentGatewayProxyConnector
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class RelationshipControllerSpec extends PlaySpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfterEach {
  val mockMongoService: RelationshipMongoService = mock[RelationshipMongoService]
  val mockAuditService = mock[AuditService]
  val mockGGProxy = mock[GovernmentGatewayProxyConnector]
  val mockRelationshipStoreController = new RelationshipController(mockGGProxy,mockAuditService,mockMongoService)
  implicit val hc = HeaderCarrier()
  override def beforeEach() {
    reset(mockMongoService)
  }

  "RelationshipStoreController" should {
    "return Status: OK Body: List(Relationship(\"AARN1234567\", \"789\", \"456\")) for finding data" in {
      when(mockMongoService.findRelationships(eqs(Relationship(Arn("AARN1234567"),"789","456")))(any()))
        .thenReturn(Future successful List(Relationship(Arn("AARN1234567"), "789", "456")))
      val response = mockRelationshipStoreController.findRelationship("AARN1234567", "789", "456")(FakeRequest("GET", ""))

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: Not Found for not finding data" in {
      when(mockMongoService.findRelationships(eqs(Relationship(Arn("ARN77777"), "789", "456")))(any())).thenReturn(Future successful List())
      val response = mockRelationshipStoreController.findRelationship("ARN77777", "789", "456")(FakeRequest("GET", ""))

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: Created for creating new record" in {
      when(mockMongoService.createRelationship(eqs(Relationship(Arn("AARN1234567"), "789", "456")))(any())).thenReturn(Future successful (()))
      when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful ("q213"))
      val response = mockRelationshipStoreController.createRelationship("AARN1234567", "789", "456")(FakeRequest("POST", ""))

      status(response) mustBe CREATED
      verify(mockMongoService, times(1)).createRelationship(any())(any())
    }

    "return Status: Ok for deleting a record" in {
      when(mockMongoService.deleteRelationship(eqs(Relationship(Arn("AARN1234567"), "789", "456")))(any())).thenReturn(Future successful true)
      when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful ("q213"))
      val response = mockRelationshipStoreController.deleteRelationship("AARN1234567", "789", "456")(FakeRequest("DELETE", ""))

      status(response) mustBe OK
      verify(mockMongoService, times(1)).deleteRelationship(any())(any())
    }
    "return Status: NotFound for failing to delete a record" in {
      when(mockMongoService.deleteRelationship(Relationship(Arn("AARN1234567"), "789", "456"))).thenReturn(Future successful false)
      when(mockGGProxy.getCredIdFor(any())(any())).thenReturn(Future successful ("q213"))
      val response = mockRelationshipStoreController.deleteRelationship("AARN1234567", "789", "456")(FakeRequest("DELETE", ""))

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).deleteRelationship(any())
    }

    "return Status: OK for finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(Relationship(Arn("AAABBB111222"), "PAYE", "123456")))(any()))
        .thenReturn(Future successful List(Relationship(Arn("AAABBB111222"), "PAYE", "123456")))
      val response = mockRelationshipStoreController.payeCheckRelationship("AAABBB111222", "123456")(FakeRequest("GET", ""))

      status(response) mustBe OK
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }

    "return Status: Not Found for not finding data via access control endpoint" in {
      when(mockMongoService.findRelationships(eqs(Relationship(Arn("AAABBB111222"), "PAYE", "123456")))(any())).thenReturn(Future successful List())
      val response = mockRelationshipStoreController.payeCheckRelationship("AAABBB111222", "123456")(FakeRequest("GET", ""))

      status(response) mustBe NOT_FOUND
      verify(mockMongoService, times(1)).findRelationships(any())(any())
    }
  }
}
