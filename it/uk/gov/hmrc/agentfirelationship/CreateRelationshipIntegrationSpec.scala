package uk.gov.hmrc.agentfirelationship

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.support.{IntegrationSpec, RelationshipActions}

class CreateRelationshipIntegrationSpec extends IntegrationSpec with GuiceOneServerPerSuite with RelationshipActions {

  feature("Create a relationship between an agent and an individual") {

    info("As an agent")
    info("I want to create a relationship with a client individual for a specific service")
    info("So that I am permitted to view the client's service/regime data")

    scenario("Create a new relationship with simple values") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      val agentId = "Agent123"
      val clientId = "Client123"
      val service = "Service123"

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = createRelationship(agentId,clientId,service)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED

      //cleanup
      deleteRelationship(agentId,clientId,service)
    }
  }


  feature("Delete a relationship between an agent and a client") {

    scenario("Delete an existing relationship between an agent and client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      val agentId = "Agent123"
      val clientId = "Client123"
      val service = "Service123"
      createRelationship(agentId,clientId,service)

      When("I call the delete-relationship endpoint")
      val deleteRelationshipResponse: WSResponse = deleteRelationship(agentId,clientId,service)

      Then("I should get a 200 OK response")
      deleteRelationshipResponse.status shouldBe OK

      And("the relationship should be deleted")
      val viewRelationshipResponse: WSResponse = getRelationship(agentId,clientId,service)
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }
  }
}
