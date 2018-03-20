package uk.gov.hmrc.agentfirelationship.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentfirelationship.stubs.{ DataStreamStub, MappingStubs }
import uk.gov.hmrc.agentfirelationship.support.{ MetricTestSupport, WireMockSupport }
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.{ HeaderCarrier, HttpGet, HttpPost }
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

class MappingConnectorSpec extends UnitSpec with OneAppPerSuite with WireMockSupport with MappingStubs with DataStreamStub with MetricTestSupport with MockitoSugar {

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

  val httpGet = app.injector.instanceOf[HttpGet]

  val mappingConnector = new MappingConnector(wireMockBaseUrl, httpGet, app.injector.instanceOf[Metrics])

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

    "fail when arn is unknown in " in {
      givenAuditConnector()
      givenArnIsUnknownFor(arn)
      an[Exception] should be thrownBy await(mappingConnector.getSaAgentReferencesFor(arn))
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