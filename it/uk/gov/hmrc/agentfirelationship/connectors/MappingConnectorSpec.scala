package uk.gov.hmrc.agentfirelationship.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.stubs.{DataStreamStub, MappingStubs}
import uk.gov.hmrc.agentfirelationship.support.{MetricTestSupport, WireMockSupport}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

class MappingConnectorSpec extends UnitSpec with GuiceOneAppPerSuite with WireMockSupport with MappingStubs with DataStreamStub with MetricTestSupport with MockitoSugar {

  override implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.agent-mapping.port" -> wireMockPort,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort)

  private implicit val hc = HeaderCarrier()
  private implicit val ec = ExecutionContext.global

  val httpClient = app.injector.instanceOf[HttpClient]
  val appConfig = app.injector.instanceOf[AppConfig]

  val mappingConnector = new MappingConnector(appConfig, httpClient, app.injector.instanceOf[Metrics])

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

    "return an empty sequence when the service returns not found" in {
      givenAuditConnector()
      givenArnNotFoundFor(arn)
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

    "record metrics for Mappings" in {
      givenArnIsKnownFor(arn, SaAgentReference("foo"))
      givenCleanMetricRegistry()
      givenAuditConnector()
      await(mappingConnector.getSaAgentReferencesFor(arn))
      timerShouldExistsAndBeenUpdated("ConsumedAPI-Digital-Mappings-GET")
    }
  }
}