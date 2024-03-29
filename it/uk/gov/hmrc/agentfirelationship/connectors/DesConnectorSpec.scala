package uk.gov.hmrc.agentfirelationship.connectors

import com.kenshoo.play.metrics.Metrics
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.stubs.{DataStreamStub, DesStubs}
import uk.gov.hmrc.agentfirelationship.support.{MetricTestSupport, WireMockSupport}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.agentfirelationship.support.UnitSpec

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class DesConnectorSpec extends UnitSpec with GuiceOneAppPerSuite with WireMockSupport with DesStubs with DataStreamStub with MetricTestSupport {

  override implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.des.port" -> wireMockPort,
        "auditing.consumer.baseUri.host" -> wireMockHost,
        "auditing.consumer.baseUri.port" -> wireMockPort)

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  private val httpClient = app.injector.instanceOf[HttpClient]
  private val appConfig = app.injector.instanceOf[AppConfig]

  val desConnector = new DesConnector(appConfig, httpClient, app.injector.instanceOf[Metrics])

  "DesConnector GetStatusAgentRelationship" should {

    val nino = Nino("AB123456C")
    val utr = Utr("1307171695")

    "return a CESA identifier when client has an active agent" in {
      val agentId = "bar"
      givenClientHasRelationshipWithAgentInCESA(nino, agentId)
      givenAuditConnector()
      await(desConnector.getClientSaAgentSaReferences(nino)) shouldBe Seq(SaAgentReference(agentId))
    }

    "return multiple CESA identifiers when client has multiple active agents" in {
      val agentIds = Seq("001", "002", "003", "004", "005", "005", "007")
      givenClientHasRelationshipWithMultipleAgentsInCESA(nino, agentIds)
      givenAuditConnector()
      await(desConnector.getClientSaAgentSaReferences(nino)) should contain theSameElementsAs agentIds.map(SaAgentReference.apply)
    }

    "return empty seq when client has no active relationship with an agent" in {
      givenClientHasNoActiveRelationshipWithAgentInCESA(nino)
      givenAuditConnector()
      await(desConnector.getClientSaAgentSaReferences(nino)) shouldBe empty
    }

    "return empty seq when client has/had no relationship with any agent" in {
      givenClientHasNoRelationshipWithAnyAgentInCESA(nino)
      givenAuditConnector()
      await(desConnector.getClientSaAgentSaReferences(nino)) shouldBe empty
    }

    "return empty seq when client's NINO not found in CESA" in {
      givenClientHasNoNinoInCESA(nino)
      givenAuditConnector()
      await(desConnector.getClientSaAgentSaReferences(nino)) shouldBe empty
    }

    "return empty seq when client relationship with agent ceased" in {
      givenClientRelationshipWithAgentCeasedInCESA(nino, "foo")
      givenAuditConnector()
      await(desConnector.getClientSaAgentSaReferences(nino)) shouldBe empty
    }

    "return empty seq when all client's relationships with agents ceased" in {
      givenAuditConnector()
      givenAllClientRelationshipsWithAgentsCeasedInCESA(nino, Seq("001", "002", "003", "004", "005", "005", "007"))
      await(desConnector.getClientSaAgentSaReferences(nino)) shouldBe empty
    }

    "fail when DES is unavailable" in {
      givenDesReturnsServiceUnavailable()
      givenAuditConnector()
      an[Exception] should be thrownBy await(desConnector.getClientSaAgentSaReferences(nino))
    }

    "fail when DES is throwing errors" in {
      givenDesReturnsServerError()
      givenAuditConnector()
      an[Exception] should be thrownBy await(desConnector.getClientSaAgentSaReferences(nino))
    }

    "record metrics for GetStatusAgentRelationship" in {
      givenClientHasRelationshipWithAgentInCESA(nino, "bar")
      givenCleanMetricRegistry()
      givenAuditConnector()
      await(desConnector.getClientSaAgentSaReferences(nino))
      timerShouldExistsAndBeenUpdated("ConsumedAPI-DES-GetStatusAgentRelationship-GET")
    }

    "return a CESA identifier when client has an active agent using Utr" in {
      val agentId = "bar"
      givenClientHasRelationshipWithAgentInCESA(utr, agentId)
      givenAuditConnector()
      await(desConnector.getClientSaAgentSaReferences(utr)) shouldBe Seq(SaAgentReference(agentId))
    }
  }
}