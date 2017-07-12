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

package uk.gov.hmrc.agentfirelationship.controllers.audit

import org.mockito.ArgumentCaptor
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Span}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{AuditEvent, DataEvent}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.{Authorization, RequestId, SessionId}
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.verify
class AduitServiceSpec extends UnitSpec with MockitoSugar with Eventually {
  implicit val testConfig = PatienceConfig(
    timeout = scaled(Span(500, Millis)),
    interval = scaled(Span(200, Millis)))
  "auditEvent" should {
    "send an Create Relationship Event event with the correct fields" in {
      val mockConnector = mock[AuditConnector]
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id"))
      )

      val auditData = new AuditData()
      auditData.set("arn", Arn("1234").value)
      auditData.set("credId", "0000001234567890")
      auditData.set("regime", "mtd-it")
      auditData.set("nino", Nino("KS969148D").value)
      await(service.sendCreateRelationshipEvent(auditData)(
        hc,
        FakeRequest("GET", "/path"))
      )

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[AuditEvent])
        verify(mockConnector).sendEvent(captor.capture())(any(), any())
        val sentEvent = captor.getValue.asInstanceOf[DataEvent]

        sentEvent.auditType shouldBe "CreateRelationship"
        sentEvent.auditSource shouldBe "agent-fi-relationship"
        sentEvent.detail("arn") shouldBe "1234"
        sentEvent.detail("credId") shouldBe "0000001234567890"
        sentEvent.detail("nino") shouldBe "KS969148D"
        sentEvent.tags.contains("Authorization") shouldBe false
        sentEvent.detail("Authorization") shouldBe "dummy bearer token"
        sentEvent.tags("transactionName") shouldBe "create-fi-relationship"
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
        requestId = Some(RequestId("dummy request id"))
      )

      val auditData = new AuditData()
      auditData.set("arn", Arn("1234").value)
      auditData.set("credId", "0000001234567890")
      auditData.set("regime", "mtd-it")
      auditData.set("nino", Nino("KS969148D").value)
      await(service.sendDeleteRelationshipEvent(auditData)(
        hc,
        FakeRequest("GET", "/path"))
      )

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[AuditEvent])
        verify(mockConnector).sendEvent(captor.capture())(any(), any())
        val sentEvent = captor.getValue.asInstanceOf[DataEvent]

        sentEvent.auditType shouldBe "EndRelationship"
        sentEvent.auditSource shouldBe "agent-fi-relationship"
        sentEvent.detail("arn") shouldBe "1234"
        sentEvent.detail("credId") shouldBe "0000001234567890"
        sentEvent.detail("nino") shouldBe "KS969148D"
        sentEvent.tags.contains("Authorization") shouldBe false
        sentEvent.detail("Authorization") shouldBe "dummy bearer token"
        sentEvent.tags("transactionName") shouldBe "end-fi-relationship"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"
      }
    }
  }
}