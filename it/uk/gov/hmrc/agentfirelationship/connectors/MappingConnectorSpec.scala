package uk.gov.hmrc.agentfirelationship.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentfirelationship.config.WSHttp
import uk.gov.hmrc.agentfirelationship.stubs.{DataStreamStub, MappingStubs}
import uk.gov.hmrc.agentfirelationship.support.{MetricTestSupport, WireMockSupport}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

class MappingConnectorSpec extends UnitSpec with OneAppPerSuite with WireMockSupport with MappingStubs with DataStreamStub with MetricTestSupport {

  override implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.agent-mapping.port" -> wireMockPort,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort
      )

  private implicit val hc = HeaderCarrier()

  val mappingConnector = new MappingConnector(wireMockBaseUrl, WSHttp, app.injector.instanceOf[Metrics])

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
      givenArnIsUnknownFor(arn)
      givenAuditConnector()
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