package uk.gov.hmrc.agentfirelationship.support

import java.net.URL

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import org.scalatest.concurrent.Eventually
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import play.api.libs.json.Json
import uk.gov.hmrc.agentfirelationship.audit.AgentClientRelationshipEvent._
import uk.gov.hmrc.agentfirelationship.stubs.{ DesStubs, MappingStubs }
import uk.gov.hmrc.play.it.Port

trait UpstreamServicesStubs extends BeforeAndAfterAll
  with BeforeAndAfterEach with Eventually with ClientUserAuthStubs with AgentAuthStubs with DesStubs with MappingStubs {

  this: Suite =>

  val wireMockHost = "localhost"
  val wireMockPort: Int = Port.randomAvailable
  val wireMockBaseUrlAsString = s"http://$wireMockHost:$wireMockPort"
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(wireMockPort))

  val fakeCredId = "fakeCredId"
  val fakeCredIdType = "GovernmentGateway"

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
    givenImplicitAuditEvents()
  }

  def givenCreatedAuditEventStub(detail: Map[String, String] = Map.empty): Unit = {
    stubFor(post(urlPathEqualTo("/write/audit"))
      .withRequestBody(similarToJson(
        s"""{
           |  "auditSource": "agent-fi-relationship",
           |  "auditType": "$AgentClientRelationshipCreated",
           |  "detail": ${Json.toJson(detail)}
           |}"""))
      .willReturn(aResponse().withStatus(204)))
  }

  def givenEndedAuditEventStub(detail: Map[String, String] = Map.empty): Unit = {
    stubFor(post(urlPathEqualTo("/write/audit"))
      .withRequestBody(similarToJson(
        s"""{
           |  "auditSource": "agent-fi-relationship",
           |  "auditType": "$ClientRemovedAgentServiceAuthorisation",
           |  "detail": ${Json.toJson(detail)}
           |}"""))
      .willReturn(aResponse().withStatus(204)))
  }

  def givenCesaCopyAuditEventStub(detail: Map[String, String] = Map.empty): Unit = {
    stubFor(post(urlPathEqualTo("/write/audit"))
      .withRequestBody(similarToJson(
        s"""{
           |  "auditSource": "agent-fi-relationship",
           |  "auditType": "$AgentClientRelationshipCreatedFromExisting",
           |  "detail": ${Json.toJson(detail)}
           |}"""))
      .willReturn(aResponse().withStatus(204)))
  }

  def givenImplicitAuditEvents() = {
    stubFor(post(urlPathEqualTo("/write/audit/merged")).willReturn(aResponse().withStatus(204)))
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
        .withBody(s"""{"authProviderId": "$fakeCredId", "authProviderIdType":"$fakeCredIdType"}""".stripMargin)))
  }

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)
}

