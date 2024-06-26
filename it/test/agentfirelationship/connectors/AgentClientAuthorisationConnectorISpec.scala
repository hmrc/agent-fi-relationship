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

package agentfirelationship.connectors

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor

import agentfirelationship.stubs.AcaStubs
import agentfirelationship.stubs.DataStreamStub
import agentfirelationship.support.MetricTestSupport
import agentfirelationship.support.WireMockSupport
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.Application
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthorisationConnector
import uk.gov.hmrc.agentfirelationship.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

class AgentClientAuthorisationConnectorISpec
    extends UnitSpec
    with GuiceOneAppPerSuite
    with WireMockSupport
    with AcaStubs
    with DataStreamStub
    with MetricTestSupport {

  implicit override lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.aca.port" -> wireMockPort,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "metrics.enabled"                -> true
      )

  private implicit val hc: HeaderCarrier            = HeaderCarrier()
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val appConfig  = app.injector.instanceOf[AppConfig]

  val connector = new AgentClientAuthorisationConnector(appConfig, httpClient, app.injector.instanceOf[Metrics])

  val arn  = Arn("KARN00001")
  val nino = Nino("AB123456C")

  "setRelationshipEnded" should {
    "return true when successful" in {
      givenSetRelationshipEndedReturns(arn, nino, "Agent", 204)
      givenAuditConnector()
      givenCleanMetricRegistry()

      await(connector.setRelationshipEnded(arn, nino.value, "Agent")) shouldBe true
      timerShouldExistsAndBeenUpdated("ConsumedAPI-SetRelationshipEnded-PUT")
    }

    "return false when invitation not found" in {
      givenSetRelationshipEndedReturns(arn, nino, "Agent", 404)
      givenAuditConnector()
      givenCleanMetricRegistry()

      await(connector.setRelationshipEnded(arn, nino.value, "Agent")) shouldBe false
      timerShouldExistsAndBeenUpdated("ConsumedAPI-SetRelationshipEnded-PUT")
    }

    "throw Exception when there was a failure" in {
      givenSetRelationshipEndedReturns(arn, nino, "Agent", 503)
      givenAuditConnector()

      an[UpstreamErrorResponse] should be thrownBy await(connector.setRelationshipEnded(arn, nino.value, "Agent"))
    }
  }

}
