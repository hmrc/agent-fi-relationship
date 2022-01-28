package uk.gov.hmrc.agentfirelationship.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.await
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.stubs.{AcaStubs, DataStreamStub}
import uk.gov.hmrc.agentfirelationship.support.{MetricTestSupport, UnitSpec, WireMockSupport}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}
import play.api.test.Helpers._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class AgentClientAuthorisationConnectorISpec extends UnitSpec with GuiceOneAppPerSuite with WireMockSupport with AcaStubs with DataStreamStub with MetricTestSupport{

  override implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.aca.port" -> wireMockPort,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort)

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  private val httpClient = app.injector.instanceOf[HttpClient]
  private val appConfig = app.injector.instanceOf[AppConfig]

  val connector = new AgentClientAuthorisationConnector(appConfig, httpClient, app.injector.instanceOf[Metrics])

  val arn = Arn("KARN00001")
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
