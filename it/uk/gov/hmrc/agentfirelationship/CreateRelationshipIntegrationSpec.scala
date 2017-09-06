package uk.gov.hmrc.agentfirelationship

import javax.inject.{Inject, Singleton}

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentfirelationship.support.{IntegrationSpec, RelationshipActions}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@Singleton
class CreateRelationshipIntegrationSpec @Inject()(mongo: RelationshipMongoService) extends IntegrationSpec with RelationshipActions with GuiceOneServerPerSuite  {

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
      val createRelationshipResponse: WSResponse = createRelationship(agentId, clientId, service)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED

      //cleanup
      deleteRelationship(agentId, clientId, service)
    }
    scenario("not Create a new relationship when agent already has 2") {

      Given("agent has two relationships ")
      val agentId = "Agent123"
      val client1Id = "Client1"
      val client2Id = "Client2"
      val client3Id = "Client3"
      val service = "afi"
      createRelationship(agentId, client1Id, service)
      createRelationship(agentId, client2Id, service)

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = createRelationship(agentId, client3Id, service)

      Then("I will receive a 403 FORBIDDEN response ")
      createRelationshipResponse.status shouldBe FORBIDDEN

      And("the new relationship should not be created")
      val getRelationshipResponse: WSResponse = getRelationship(agentId, client3Id, service)
      getRelationshipResponse.status shouldBe NOT_FOUND

      //cleanup
      deleteRelationship(agentId, client1Id, service)
      deleteRelationship(agentId, client2Id, service)

    }
    scenario("A relationship which is the same already exists") {

      Given("agent has a relationship")
      val agentId = "Agent123"
      val client1Id = "Client1"
      val service = "afi"
      val relationship = Relationship(Arn(agentId), service, client1Id)
      createRelationship(agentId, client1Id, service)

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = createRelationship(agentId, client1Id, service)

      Then("I will receive a 201 response ")
      createRelationshipResponse.status shouldBe CREATED

      And("the new relationship should not be created")
      val agentRelationships: Future[List[Relationship]] = mongo.findRelationships(relationship)
      Await.result(agentRelationships, 10000 millis).length shouldBe 1
      //cleanup
      deleteRelationship(agentId, client1Id, service)
    }
  }


  feature("Delete a relationship between an agent and a client") {

    scenario("Delete an existing relationship between an agent and client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      val agentId = "Agent123"
      val clientId = "Client123"
      val service = "Service123"
      createRelationship(agentId, clientId, service)

      When("I call the delete-relationship endpoint")
      val deleteRelationshipResponse: WSResponse = deleteRelationship(agentId, clientId, service)

      Then("I should get a 200 OK response")
      deleteRelationshipResponse.status shouldBe OK

      And("the relationship should be deleted")
      val viewRelationshipResponse: WSResponse = getRelationship(agentId, clientId, service)
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }
  }
}
