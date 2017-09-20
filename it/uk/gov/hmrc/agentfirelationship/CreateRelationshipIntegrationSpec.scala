package uk.gov.hmrc.agentfirelationship

import javax.inject.Singleton

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentfirelationship.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

@Singleton
class CreateRelationshipIntegrationSpec extends IntegrationSpec with UpstreamServicesStubs
  with RelationshipActions with GuiceOneServerPerSuite with MongoApp {
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

  feature("Create a relationship between an agent and an individual") {

    info("As an agent")
    info("I want to create a relationship with a client individual for a specific service")
    info("So that I am permitted to view the client's service/regime data")

    scenario("Create a new relationship with simple values") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED
    }

    scenario("A relationship which is the same already exists") {

      Given("agent has a relationship")
      givenCreatedAuditEventStub(auditDetails)
      val relationship = Relationship(Arn(agentId), service, clientId)
      Await.result(createRelationship(agentId, clientId, service), 10 seconds)

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 201 response ")
      createRelationshipResponse.status shouldBe CREATED

      And("the new relationship should not be created")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(relationship)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }
  }

  feature("Delete a relationship between an agent and a client") {

    scenario("Delete an existing relationship between an agent and client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      Await.result(createRelationship(agentId, clientId, service), 10 seconds)

      When("I call the delete-relationship endpoint")
      val deleteRelationshipResponse: WSResponse = Await.result(deleteRelationship(agentId, clientId, service), 10 seconds)

      Then("I should get a 200 OK response")
      deleteRelationshipResponse.status shouldBe OK

      And("the relationship should be deleted")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }
  }
}
