package uk.gov.hmrc.agentfirelationship

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentfirelationship.support.{IntegrationSpec, RelationshipActions}

class FailuresIntegrationSpec extends IntegrationSpec with GuiceOneServerPerSuite with RelationshipActions {

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> "mongodb://nowhere:27017/none"
      )

  feature("Do not handle infrastructure failures, propagates errors downstream") {

    scenario("Mongodb not available when creating relationship") {
      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      val agentId = "Agent123"
      val clientId = "Client123"
      val service = "Service123"
      val response = createRelationship(agentId, clientId, service)
      When("I call the create-relationship endpoint")
      response.status shouldBe INTERNAL_SERVER_ERROR
      Then("I will receive a 500 INTERNAL SERVER ERROR response")
    }

    scenario("Mongodb not available when deleting relationship") {
      Given("there exists a relationship between an agent and client for a given service")
      val agentId = "Agent123"
      val clientId = "Client123"
      val service = "Service123"

      When("I call the delete-relationship endpoint")
      val response = deleteRelationship(agentId,clientId,service)

      Then("I should get a 500 INTERNAL SERVER ERROR response")
      response.status shouldBe INTERNAL_SERVER_ERROR
    }

    scenario("Mongodb not available when viewing relationship") {
      Given("there exists a relationship between an agent and client for a given service")
      val agentId = "Agent123"
      val clientId = "Client123"
      val service = "Service123"
      createRelationship(agentId,clientId,service)

      When("I call the View Relationship endpoint")
      val response = getRelationship(agentId, clientId, service)

      Then("I will receive a 500 INTERNAL SERVER ERROR response")
      response.status shouldBe INTERNAL_SERVER_ERROR
    }
  }


}
