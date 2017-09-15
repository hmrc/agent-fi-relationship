package uk.gov.hmrc.agentfirelationship.support

import java.net.URL

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.skyscreamer.jsonassert.JSONCompareMode
import play.api.libs.json.Json
import uk.gov.hmrc.agentfirelationship.audit.AgentClientRelationshipEvent._
import uk.gov.hmrc.play.it.Port

trait UpstreamServicesStubs extends BeforeAndAfterAll with BeforeAndAfterEach with Eventually {

  this: Suite =>

  val wireMockHost = "localhost"
  val wireMockPort: Int = Port.randomAvailable
  val wireMockBaseUrlAsString = s"http://$wireMockHost:$wireMockPort"
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort))

  val fakeCredId = "fakeCredId"

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    configureFor(wireMockHost, wireMockPort)
    wireMockServer.start()
  }

  override protected def afterAll(): Unit = {
    wireMockServer.stop()
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset()
    givenAuthReturnsUserDetails()
  }

  def givenCreatedAuditEventStub(detail: Map[String, String] = Map.empty): Unit = {
    stubFor(post(urlPathEqualTo("/write/audit"))
      .withRequestBody(similarToJson(
        s"""{
           |  "auditSource": "agent-fi-relationship",
           |  "auditType": "$AgentClientRelationshipCreated",
           |  "detail": ${Json.toJson(detail)}
           |}"""
      ))
      .willReturn(aResponse().withStatus(200)))
  }

  def givenEndedAuditEventStub(detail: Map[String, String] = Map.empty): Unit = {
    stubFor(post(urlPathEqualTo("/write/audit"))
      .withRequestBody(similarToJson(
        s"""{
           |  "auditSource": "agent-fi-relationship",
           |  "auditType": "$AgentClientRelationshipEnded",
           |  "detail": ${Json.toJson(detail)}
           |}"""
      ))
      .willReturn(aResponse().withStatus(200)))
  }

  def givenAuthReturnsUserDetails(): Unit = {
    val oid: String = "556737e15500005500eaf68f"
    val wireMockBaseUrlAsString = s"http://$wireMockHost:$wireMockPort"
    val wireMockBaseUrl = new URL(wireMockBaseUrlAsString)

    stubFor(get(urlEqualTo("/auth/authority"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""{ "userDetailsLink":"$wireMockBaseUrl/user-details/id/$oid" }""".stripMargin)))

    stubFor(get(urlPathEqualTo(s"/user-details/id/$oid"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""{"authProviderId": "$fakeCredId"}""".stripMargin)))
  }

  private def similarToJson(value: String) = equalToJson(value.stripMargin, JSONCompareMode.LENIENT)
}


