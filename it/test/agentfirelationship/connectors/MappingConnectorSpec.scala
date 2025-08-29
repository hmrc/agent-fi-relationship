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

import agentfirelationship.stubs.DataStreamStub
import agentfirelationship.stubs.MappingStubs
import agentfirelationship.support.WireMockSupport
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.Application
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.connectors.MappingConnector
import uk.gov.hmrc.agentfirelationship.models.Arn
import uk.gov.hmrc.agentfirelationship.support.UnitSpec
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

class MappingConnectorSpec
    extends UnitSpec
    with GuiceOneAppPerSuite
    with WireMockSupport
    with MappingStubs
    with DataStreamStub
    with MockitoSugar {

  implicit override lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.agent-mapping.port" -> wireMockPort,
        "auditing.consumer.baseUri.host"           -> wireMockHost,
        "auditing.consumer.baseUri.port"           -> wireMockPort,
        "metrics.enabled"                          -> true
      )

  private implicit val hc: HeaderCarrier            = HeaderCarrier()
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
  val appConfig: AppConfig     = app.injector.instanceOf[AppConfig]

  val mappingConnector: MappingConnector = new MappingConnector(appConfig, httpClient, app.injector.instanceOf[Metrics])

  "MappingConnector" should {

    val arn = Arn("foo")

    "return CESA agent reference for some known ARN" in {
      givenArnIsKnownFor(arn, SaAgentReference("foo"))
      givenAuditConnector()
      await(mappingConnector.getSaAgentReferencesFor(arn)) shouldBe Seq(SaAgentReference("foo"))
    }

    "return multiple CESA agent reference for some known ARN" in {
      val references = Seq(SaAgentReference("001"), SaAgentReference("002"))
      givenArnIsKnownFor(arn, references)
      givenAuditConnector()
      await(mappingConnector.getSaAgentReferencesFor(arn)) shouldBe references
    }

    "return an empty sequence when there are no agent refs found" in {
      givenAuditConnector()
      givenAgentRefsNotFoundFor(arn)
      await(mappingConnector.getSaAgentReferencesFor(arn)) shouldBe Seq.empty
    }

    "fail when mapping service is unavailable" in {
      givenServiceReturnsServiceUnavailable()
      givenAuditConnector()
      an[Exception] should be thrownBy await(mappingConnector.getSaAgentReferencesFor(arn))
    }

    "fail when mapping service is throwing errors" in {
      givenServiceReturnsServerError()
      givenAuditConnector()
      an[Exception] should be thrownBy await(mappingConnector.getSaAgentReferencesFor(arn))
    }

  }
}
