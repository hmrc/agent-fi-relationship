package uk.gov.hmrc.agentfirelationship

import java.time.LocalDateTime
import javax.inject.Singleton
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.{Active, Terminated}
import uk.gov.hmrc.agentfirelationship.models.{Relationship, RelationshipStatus}
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentfirelationship.stubs.AcaStubs
import uk.gov.hmrc.agentfirelationship.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import language.postfixOps

@Singleton
class CreateRelationshipIntegrationSpec extends IntegrationSpec with UpstreamServicesStubs
with RelationshipActions with GuiceOneServerPerSuite with MongoApp with AcaStubs {
  me: DualSuite =>

  def repo: RelationshipMongoService = app.injector.instanceOf[RelationshipMongoService]

  override implicit lazy val app: Application = appBuilder.build()
  override def arn = agentId
  override def nino = clientId

  val testResponseDate = LocalDateTime.now
  val validTestRelationship: Relationship = Relationship(Arn(arn), service, nino, Some(Active), testResponseDate, None)

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships" -> false,
        "features.check-cesa-relationships" -> false,
      "microservice.services.aca.port" -> wireMockPort)

  Feature("Create a relationship between an agent and an individual as an agent") {

    info("I want to create a relationship with a client individual for a specific service")
    info("So that I am permitted to view the client's service/regime data")

    Scenario("Create a new relationship with simple values") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInAndIsSubscribedAsAgent
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED

      val agentRelationship: Relationship = Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds).head

      And("Confirm the relationship contains the start date")
      agentRelationship.startDate shouldBe testResponseDate

      And("Confirm the relationship contains the relationship status as ACTIVE")
      agentRelationship.relationshipStatus shouldBe Some(RelationshipStatus.Active)
    }

    Scenario("A relationship which is the same already exists") {

      isLoggedInAndIsSubscribedAsAgent

      Given("agent has a relationship")
      givenCreatedAuditEventStub(auditDetails)
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 response ")
      createRelationshipResponse.status shouldBe CREATED

      And("the new relationship should not be created")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }

    Scenario("A relationship for same service already exists with different agent") {

      isLoggedInAndIsSubscribedAsAgent(agentId2)
      Given("agent 2 has a relationship")
      givenCreatedAuditEventStub(auditDetails)
      val createRelationshipResponse1: WSResponse = Await.result(createRelationship(agentId2, clientId, service, testResponseDate), 10 seconds)
      Then("I will receive a 201 response ")
      createRelationshipResponse1.status shouldBe CREATED

      And("the new relationship should be created")
      val agentRelationships1: Future[List[Relationship]] = repo.findRelationships(agentId2, service, clientId)
      Await.result(agentRelationships1, 10 seconds).length shouldBe 1

      isLoggedInAndIsSubscribedAsAgent

      When("I call the create-relationship for same service for agent 1")
      givenSetRelationshipEndedReturns(Arn(agentId2), Nino(clientId), "Agent", 204)
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 response ")
      createRelationshipResponse.status shouldBe CREATED

      And("the new relationship should be created")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1

      And("the relationship with agent 2 should be terminated")
      Await.result(repo.findRelationships(agentId2, service, clientId, Terminated), 10 seconds).length shouldBe 1
    }

    Scenario("The user is not logged in with GG credentials") {
      isNotLoggedIn
      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 401 response ")
      createRelationshipResponse.status shouldBe UNAUTHORIZED
    }

    Scenario("The user does not have an affinity group") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInWithoutAffinityGroup
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      createRelationshipResponse.status shouldBe FORBIDDEN
    }

    Scenario("The user has invalid enrolments") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInWithInvalidEnrolments
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      createRelationshipResponse.status shouldBe FORBIDDEN
    }
  }

  Feature("Create a relationship between an agent and an individual as a client") {

    info("I want to create a relationship with a client individual for a specific service")
    info("So that I am permitted to view the client's service/regime data")

    Scenario("Create a new relationship with simple values") {

      isLoggedInAsClient

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED

      And("Confirm the relationship contains the start date")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId)
      Await.result(agentRelationships, 10 seconds).head.startDate shouldBe testResponseDate
    }

    Scenario("A relationship which is the same already exists") {

      isLoggedInAsClient

      Given("agent has a relationship")
      givenCreatedAuditEventStub(auditDetails)
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 response ")
      createRelationshipResponse.status shouldBe CREATED

      And("the new relationship should not be created")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }
  }
}
