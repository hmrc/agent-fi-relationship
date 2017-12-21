package uk.gov.hmrc.agentfirelationship

import java.time.LocalDateTime
import javax.inject.Singleton

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.{Active, Terminated}
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentfirelationship.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

@Singleton
class TerminateRelationshipIntegrationSpec extends IntegrationSpec with UpstreamServicesStubs
  with RelationshipActions with GuiceOneServerPerSuite with MongoApp {
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
        "features.check-cesa-relationships" -> false
      )

  feature("Terminate a relationship between an agent and a client") {

    scenario("Agent terminates an existing relationship with client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAndIsSubscribedAsAgent
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the terminates relationship endpoint")
      val terminateRelationshipResponse: WSResponse = Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I should get a 200 OK response")
      terminateRelationshipResponse.status shouldBe OK

      And("the relationship should be terminated")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }

    scenario("Agent fails to terminates the relation with client for an invalid service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAndIsSubscribedAsAgent
      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)

      When("I call the terminates relationship endpoint")
      val terminateRelationshipResponse: WSResponse = Await.result(terminateRelationship(agentId, clientId, "INVALID"), 10 seconds)

      Then("I should get a 404 NotFound response")
      terminateRelationshipResponse.status shouldBe NOT_FOUND

      And("the relationship should remain unaffected")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId, Active)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }

    scenario("Agent fails to terminates the relation with client for an invalid agent id") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAndIsSubscribedAsAgent
      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)

      When("I call the terminates relationship endpoint")
      val terminateRelationshipResponse: WSResponse = Await.result(terminateRelationship(agentId2, clientId, service), 10 seconds)

      Then("I should get a 403 Forbidden response")
      terminateRelationshipResponse.status shouldBe FORBIDDEN
    }

    scenario("Client terminates all of client's agents, setting status to terminated for all"){
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAsClient

      Given("there exists a relationship or more for particular clientId")
      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)
      Await.result(repo.createRelationship(validTestRelationship.copy(arn = Arn(agentId2))), 10 seconds)

      When("I call the terminate relationship endpoint")
      val terminateRelationshipResponse: WSResponse = Await.result(terminateClientRelationships(clientId, service), 10 seconds)

      Then("I should get a 200 OK response")
      terminateRelationshipResponse.status shouldBe OK

      And("the relationship should be terminated")
      val agentRelationships: Future[List[Relationship]] = repo.findClientRelationships(service, clientId, Terminated)
      Await.result(agentRelationships, 10 seconds).length shouldBe 2
    }

    scenario("Client fails to terminate all of client's agents for an invalid service"){
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAsClient

      Given("there exists a relationship or more for particular clientId")
      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)
      Await.result(repo.createRelationship(validTestRelationship.copy(arn = Arn(agentId2))), 10 seconds)

      When("I call the terminate relationship endpoint")
      val terminateRelationshipResponse: WSResponse = Await.result(terminateClientRelationships(clientId, "INVALID"), 10 seconds)

      Then("I should get a 500 response")
      terminateRelationshipResponse.status shouldBe INTERNAL_SERVER_ERROR

      And("the relationship should be terminated")
      val agentRelationships: Future[List[Relationship]] = repo.findClientRelationships(service, clientId, Active)
      Await.result(agentRelationships, 10 seconds).length shouldBe 2
    }

    scenario("Client fails to terminate all of client's agents for an invalid client id"){
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAsClient

      Given("there exists a relationship or more for particular clientId")
      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)
      Await.result(repo.createRelationship(validTestRelationship.copy(arn = Arn(agentId2))), 10 seconds)

      When("I call the terminate relationship endpoint")
      val terminateRelationshipResponse: WSResponse = Await.result(terminateClientRelationships("AE123456A", service), 10 seconds)

      Then("I should get a 403 Forbidden response")
      terminateRelationshipResponse.status shouldBe FORBIDDEN
    }

    scenario("The user is not logged in with GG credentials") {
      isNotLoggedIn
      When("I call the create-relationship endpoint")
      val terminateRelationshipResponse: WSResponse = Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 401 response ")
      terminateRelationshipResponse.status shouldBe UNAUTHORIZED
    }

    scenario("The user does not have an affinity group") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInWithoutAffinityGroup
      val terminateRelationshipResponse: WSResponse = Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      terminateRelationshipResponse.status shouldBe FORBIDDEN
    }

    scenario("The user has invalid enrolments") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInWithInvalidEnrolments
      val terminateRelationshipResponse: WSResponse = Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      terminateRelationshipResponse.status shouldBe FORBIDDEN
    }
  }
}
