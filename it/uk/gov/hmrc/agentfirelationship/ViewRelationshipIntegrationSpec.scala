package uk.gov.hmrc.agentfirelationship

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.support.{IntegrationSpec, RelationshipActions}

class ViewRelationshipIntegrationSpec extends IntegrationSpec with GuiceOneServerPerSuite with RelationshipActions {

  feature("View relationships for a client individual") {

    scenario("Agent views an existing relationship") {

      Given("there exists a relationship between an agent and client for a given service")
      val agentId = "Agent123"
      val clientId = "Client123"
      val service = "Service123"
      createRelationship(agentId,clientId,service)

      When("I call the View Relationship endpoint")
      val viewRelationshipResponse: WSResponse = getRelationship(agentId, clientId, service)

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

      //cleanup
      deleteRelationship(agentId,clientId,service)
    }

    scenario("Agent views a non-existent relationship") {

      Given("no relationship exists for a combination of agent, client and service")
      val agentId = "Agent123"
      val clientId = "Client123"
      val service = "Service123"

      When("I call the View Relationship endpoint")
      val viewRelationshipResponse: WSResponse = getRelationship(agentId, clientId, service)

      Then("I will receive a 404 NOT FOUND response")
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }
  }
}
