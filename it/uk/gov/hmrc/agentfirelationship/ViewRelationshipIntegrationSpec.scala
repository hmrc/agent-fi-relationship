package uk.gov.hmrc.agentfirelationship

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentfirelationship.support._

import scala.concurrent.Await
import scala.concurrent.duration._

class ViewRelationshipIntegrationSpec extends IntegrationSpec with UpstreamServicesStubs
  with GuiceOneServerPerSuite with RelationshipActions with MongoApp {
  me: DualSuite =>

  def repo: RelationshipMongoService = app.injector.instanceOf[RelationshipMongoService]

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}"
      )

  feature("View relationships for a client individual") {

    scenario("Agent views an existing relationship") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      Await.result(createRelationship(agentId, clientId, service, validDateFormatInString), 10 seconds)

      When("I call the View Relationship endpoint")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 200 OK response")
      viewRelationshipResponse.status shouldBe OK

      And("The response body will contain the relationship details")
      val jsonResponse = Json.parse(viewRelationshipResponse.body)
      val actualAgentId = (jsonResponse(0) \ "arn").as[String]
      val actualClientId = (jsonResponse(0) \ "clientId").as[String]
      val actualService = (jsonResponse(0) \ "service").as[String]
      actualAgentId shouldBe agentId
      actualClientId shouldBe clientId
      actualService shouldBe service
    }

    scenario("Agent views a non-existent relationship") {

      Given("no relationship exists for a combination of agent, client and service")

      When("I call the View Relationship endpoint")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 404 NOT FOUND response")
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }
  }
}
