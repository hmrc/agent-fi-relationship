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

package uk.gov.hmrc.agentfirelationship.controllers.audit

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Span}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.{Authorization, RequestId, SessionId}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.test.UnitSpec

class AuditServiceSpec extends UnitSpec with MockitoSugar with Eventually {
  implicit val testConfig = PatienceConfig(timeout = scaled(Span(500, Millis)), interval = scaled(Span(200, Millis)))
  "auditEvent" should {
    "send an Create Relationship Event event with the correct fields" in {
      val mockConnector = mock[AuditConnector]
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id")))

      val auditData = new AuditData()
      auditData.set("arn", Arn("1234").value)
      auditData.set("authProviderId", "0000001234567890")
      auditData.set("service", "personal-income-record")
      auditData.set("clientId", Nino("KS969148D").value)
      auditData.set("clientIdType", "ni")
      await(service.sendCreateRelationshipEvent(auditData)(hc, FakeRequest("GET", "/path")))

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockConnector).sendEvent(captor.capture())(any(), any())
        val sentEvent = captor.getValue.asInstanceOf[DataEvent]

        sentEvent.auditType shouldBe "AgentClientRelationshipCreated"
        sentEvent.auditSource shouldBe "agent-fi-relationship"
        sentEvent.detail("arn") shouldBe "1234"
        sentEvent.detail("authProviderId") shouldBe "0000001234567890"
        sentEvent.detail("service") shouldBe "personal-income-record"
        sentEvent.detail("clientId") shouldBe "KS969148D"
        sentEvent.detail("clientIdType") shouldBe "ni"
        sentEvent.tags.contains("Authorization") shouldBe false
        sentEvent.detail("Authorization") shouldBe "dummy bearer token"
        sentEvent.tags("transactionName") shouldBe "agent fi create relationship"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"
      }
    }
    "send an Delete Relationship Event event with the correct fields" in {
      val mockConnector = mock[AuditConnector]
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id")))

      val auditData = new AuditData()
      auditData.set("agentReferenceNumber", Arn("1234").value)
      auditData.set("authProviderId", "0000001234567890")
      auditData.set("service", "personal-income-record")
      auditData.set("clientId", Nino("KS969148D").value)
      auditData.set("clientIdType", "ni")
      await(service.sendTerminatedRelationshipEvent(auditData)(hc, FakeRequest("GET", "/path")))

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockConnector).sendEvent(captor.capture())(any(), any())
        val sentEvent = captor.getValue.asInstanceOf[DataEvent]

        sentEvent.auditType shouldBe "ClientTerminatedAgentServiceAuthorisation"
        sentEvent.auditSource shouldBe "agent-fi-relationship"
        sentEvent.detail("agentReferenceNumber") shouldBe "1234"
        sentEvent.detail("authProviderId") shouldBe "0000001234567890"
        sentEvent.detail("service") shouldBe "personal-income-record"
        sentEvent.detail("clientId") shouldBe "KS969148D"
        sentEvent.detail("clientIdType") shouldBe "ni"
        sentEvent.tags.contains("Authorization") shouldBe false
        sentEvent.detail("Authorization") shouldBe "dummy bearer token"
        sentEvent.tags("transactionName") shouldBe "client terminated agent:service authorisation"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"
      }
    }

    "send an Create Relationship From Existing event with correct fields" in {
      val mockConnector = mock[AuditConnector]
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id")))

      val auditData = new AuditData()
      auditData.set("agentReferenceNumber", Arn("1234").value)
      auditData.set("saAgentRef", "SA6012")
      auditData.set("service", "afi")
      auditData.set("clientId", Nino("KS969148D").value)
      auditData.set("clientIdType", "ni")
      await(service.sendCreateRelationshipFromExisting(auditData)(hc, FakeRequest("GET", "/path")))

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockConnector).sendEvent(captor.capture())(any(), any())
        val sentEvent = captor.getValue.asInstanceOf[DataEvent]

        sentEvent.auditType shouldBe "AgentClientRelationshipCreatedFromExisting"
        sentEvent.auditSource shouldBe "agent-fi-relationship"
        sentEvent.detail("agentReferenceNumber") shouldBe "1234"
        sentEvent.detail("saAgentRef") shouldBe "SA6012"
        sentEvent.detail("service") shouldBe "afi"
        sentEvent.detail("clientId") shouldBe "KS969148D"
        sentEvent.detail("clientIdType") shouldBe "ni"
        sentEvent.tags.contains("Authorization") shouldBe false
        sentEvent.detail("Authorization") shouldBe "dummy bearer token"
        sentEvent.tags("transactionName") shouldBe "Agent client relationship created from CESA"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"
      }
    }

    "sendHmrcLedDeleteRelationshipAuditEvent with correct fields" in {
      val mockConnector = mock[AuditConnector]
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id")))

      val auditData = new AuditData()
      auditData.set("authProviderId", "0000001234567890")
      auditData.set("authProviderIdType", "PrivilegedApplication")
      auditData.set("agentReferenceNumber", Arn("1234"))
      auditData.set("clientId", Nino("KS969148D").value)
      auditData.set("service", "personal-income-record")

      await(service.sendHmrcLedDeleteRelationshipAuditEvent(auditData)(hc, FakeRequest("GET", "/path")))

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockConnector).sendEvent(captor.capture())(any(), any())
        val sentEvent = captor.getValue.asInstanceOf[DataEvent]

        sentEvent.auditType shouldBe "HmrcRemovedAgentServiceAuthorisation"
        sentEvent.auditSource shouldBe "agent-fi-relationship"
        sentEvent.detail("authProviderId") shouldBe "0000001234567890"
        sentEvent.detail("authProviderIdType") shouldBe "PrivilegedApplication"
        sentEvent.detail("agentReferenceNumber") shouldBe "1234"
        sentEvent.detail("service") shouldBe "personal-income-record"
        sentEvent.detail("clientId") shouldBe "KS969148D"
        sentEvent.tags.contains("Authorization") shouldBe false
        sentEvent.detail("Authorization") shouldBe "dummy bearer token"
        sentEvent.tags("transactionName") shouldBe "hmrc remove agent:service authorisation"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"
      }
    }
  }
}
