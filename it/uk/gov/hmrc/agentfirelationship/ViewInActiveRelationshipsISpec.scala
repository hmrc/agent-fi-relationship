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
import scala.concurrent.ExecutionContext.Implicits.global

class ViewInActiveRelationshipsISpec extends IntegrationSpec with UpstreamServicesStubs
  with GuiceOneServerPerSuite with RelationshipActions with MongoApp {
  me: DualSuite =>

  def repo: RelationshipMongoService = app.injector.instanceOf[RelationshipMongoService]

  override def arn = agentId
  override def nino = clientId

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships" -> false,
        "features.check-cesa-relationships" -> false)

  feature("View inactive relationships for an agent") {

    scenario("Agent views an existing relationships that are inactive") {

      Given("there are inactive relationships between an agent and their clients")
      isLoggedInAndIsSubscribedAsAgent

      givenCreatedAuditEventStub(auditDetails)
      givenCreatedAuditEventStub(auditDetails2)

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)
      val createRelationshipResponse2: WSResponse = Await.result(createRelationship(agentId, clientId2, service, testResponseDate), 10 seconds)


      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED
      createRelationshipResponse2.status shouldBe CREATED

      When("I call the terminates relationship endpoint")
      val terminateRelationshipResponse: WSResponse = Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)
      val terminateRelationshipResponse2: WSResponse = Await.result(terminateRelationship(agentId, clientId2, service), 10 seconds)


      Then("I should get a 200 OK response")
      terminateRelationshipResponse.status shouldBe OK
      terminateRelationshipResponse2.status shouldBe OK

      When("I call the View Inactive Relationship endpoint")
      val viewInactiveRelationshipResponse: WSResponse = Await.result(getInactiveRelationships, 10 seconds)

      Then("I will receive a 200 OK response")
      viewInactiveRelationshipResponse.status shouldBe OK

      And("The response body will contain the relationship details")
      val jsonResponse = Json.parse(viewInactiveRelationshipResponse.body)
      val actualAgentId = (jsonResponse(0) \ "arn").as[String]
      val actualClientId = (jsonResponse(0) \ "clientId").as[String]
      val actualService = (jsonResponse(0) \ "service").as[String]
      val actualClientId2 = (jsonResponse(1) \ "clientId").as[String]
      actualAgentId shouldBe agentId
      actualClientId shouldBe clientId
      actualService shouldBe service
      actualClientId2 shouldBe clientId2
    }

    scenario("Agent does not find any inactive relationships") {

      Given("no inactive relationship exists for an agent")
      Await.result(repo.findInActiveAgentRelationships(agentId), 10 seconds) shouldBe empty

      When("I call the View Inactive Relationship endpoint")
      val viewInactiveRelationshipResponse: WSResponse = Await.result(getInactiveRelationships, 10 seconds)

      Then("I will receive a 404 NOT FOUND response")
      viewInactiveRelationshipResponse.status shouldBe NOT_FOUND
    }
  }

}
