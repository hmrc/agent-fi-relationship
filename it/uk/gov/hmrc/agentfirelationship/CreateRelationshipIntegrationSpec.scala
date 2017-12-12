package uk.gov.hmrc.agentfirelationship

import java.time.LocalDateTime
import javax.inject.Singleton

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.models.{Relationship, RelationshipStatus}
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
  override def arn = agentId
  override def nino = clientId

  val testResponseDate: String = LocalDateTime.now.toString
  val validTestRelationship: Relationship = Relationship(Arn(arn), service, nino, Active, LocalDateTime.parse(testResponseDate), None)

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships" -> false,
        "features.check-cesa-relationships" -> false
      )

  feature("Create a relationship between an agent and an individual as an agent") {

    info("I want to create a relationship with a client individual for a specific service")
    info("So that I am permitted to view the client's service/regime data")

    scenario("Create a new relationship with simple values") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInAndIsSubscribedAsAgent
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED

      val agentRelationship: Relationship = Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds).head

      And("Confirm the relationship contains the start date")
      agentRelationship.startDate.toString shouldBe testResponseDate

      And("Confirm the relationship contains the relationship status as ACTIVE")
      agentRelationship.relationshipStatus shouldBe RelationshipStatus.Active
    }

    scenario("A relationship which is the same already exists") {

      Given("agent has a relationship")
      givenCreatedAuditEventStub(auditDetails)
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the create-relationship endpoint")
      isLoggedInAndIsSubscribedAsAgent
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 response ")
      createRelationshipResponse.status shouldBe CREATED

      And("the new relationship should not be created")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }

    scenario("The user is not logged in with GG credentials") {
      isNotLoggedIn
      When("I call the create-relationship endpoint")
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 401 response ")
      createRelationshipResponse.status shouldBe UNAUTHORIZED
    }

    scenario("The user does not have an affinity group") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInWithoutAffinityGroup
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      createRelationshipResponse.status shouldBe FORBIDDEN
    }

    scenario("The user has invalid enrolments") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInWithInvalidEnrolments
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      createRelationshipResponse.status shouldBe FORBIDDEN
    }
  }

  feature("Create a relationship between an agent and an individual as a client") {

    info("I want to create a relationship with a client individual for a specific service")
    info("So that I am permitted to view the client's service/regime data")

    scenario("Create a new relationship with simple values") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInAsClient
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED

      And("Confirm the relationship contains the start date")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId)
      Await.result(agentRelationships, 10 seconds).head.startDate.toString shouldBe testResponseDate
    }

    scenario("A relationship which is the same already exists") {

      Given("agent has a relationship")
      givenCreatedAuditEventStub(auditDetails)
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the create-relationship endpoint")
      isLoggedInAsClient
      val createRelationshipResponse: WSResponse = Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 response ")
      createRelationshipResponse.status shouldBe CREATED

      And("the new relationship should not be created")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }
  }
}
