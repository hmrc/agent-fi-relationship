/*
 * Copyright 2024 HM Revenue & Customs
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

package agentfirelationship.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.Eventually
import org.scalatest.time.Millis
import org.scalatest.time.Seconds
import org.scalatest.time.Span
import play.api.libs.json.Json
import uk.gov.hmrc.agentfirelationship.audit.AgentClientRelationshipEvent.AgentClientRelationshipEvent

trait DataStreamStub extends Eventually {

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(scaled(Span(2, Seconds)), scaled(Span(500, Millis)))

  def verifyAuditRequestSent(
      count: Int,
      event: AgentClientRelationshipEvent,
      tags: Map[String, String] = Map.empty,
      detail: Map[String, String] = Map.empty
  ): Unit = {
    eventually {
      verify(
        count,
        postRequestedFor(urlPathEqualTo(auditUrl))
          .withRequestBody(similarToJson(s"""{
             |  "auditSource": "agent-fi-relationship",
             |  "auditType": "$event",
             |  "tags": ${Json.toJson(tags)},
             |  "detail": ${Json.toJson(detail)}
             |}"""))
      )
    }
  }

  def verifyAuditRequestNotSent(event: AgentClientRelationshipEvent): Unit = {
    eventually {
      verify(
        0,
        postRequestedFor(urlPathEqualTo(auditUrl))
          .withRequestBody(similarToJson(s"""{
             |  "auditSource": "agent-fi-relationship",
             |  "auditType": "$event"
             |}"""))
      )
    }
  }

  def givenAuditConnector(): StubMapping = {
    stubFor(post(urlPathMatching(auditUrl + "/merged")).willReturn(aResponse().withStatus(204)))
    stubFor(post(urlPathMatching(auditUrl)).willReturn(aResponse().withStatus(204)))
  }

  private def auditUrl: String = "/write/audit"

  private def similarToJson(value: String): StringValuePattern = equalToJson(value.stripMargin, true, true)

}
