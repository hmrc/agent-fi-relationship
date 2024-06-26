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
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.SaAgentReference

trait MappingStubs {

  def givenArnIsKnownFor(arn: Arn, saAgentReference: SaAgentReference): StubMapping = {
    stubFor(
      get(urlEqualTo(s"/agent-mapping/mappings/sa/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{"mappings":[{"arn":"${arn.value}","saAgentReference":"${saAgentReference.value}"}]}""")
        )
    )
  }

  def givenArnIsKnownFor(arn: Arn, refs: Seq[SaAgentReference]): StubMapping = {
    stubFor(
      get(urlEqualTo(s"/agent-mapping/mappings/sa/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(
              s"""{"mappings":[${refs.map(ref => s"""{"arn":"${arn.value}","saAgentReference":"${ref.value}"}""").mkString(",")}]}"""
            )
        )
    )
  }

  def givenAgentRefsNotFoundFor(arn: Arn): StubMapping = {
    stubFor(
      get(urlEqualTo(s"/agent-mapping/mappings/sa/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(404)
        )
    )
  }

  def givenServiceReturnsServerError(): StubMapping = {
    stubFor(
      get(urlMatching(s"/agent-mapping/.*"))
        .willReturn(aResponse().withStatus(500))
    )
  }

  def givenServiceReturnsServiceUnavailable(): StubMapping = {
    stubFor(
      get(urlMatching(s"/agent-mapping/.*"))
        .willReturn(aResponse().withStatus(503))
    )
  }

}
