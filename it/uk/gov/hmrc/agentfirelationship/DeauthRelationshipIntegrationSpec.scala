package uk.gov.hmrc.agentfirelationship

import java.time.LocalDateTime
import javax.inject.Singleton

import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.{Active, Inactive}
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentfirelationship.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import scala.concurrent.{Await, Future}

@Singleton
class DeauthRelationshipIntegrationSpec extends IntegrationSpec with UpstreamServicesStubs
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

  feature("De-authorise a relationship between an agent and a client") {

    scenario("De-authorise an existing relationship between an agent and client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAndIsSubscribedAsAgent
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the deauth relationship endpoint")
      val deleteRelationshipResponse: WSResponse = Await.result(deleteRelationship(agentId, clientId, service), 10 seconds)

      Then("I should get a 200 OK response")
      deleteRelationshipResponse.status shouldBe OK

      And("the relationship should be deleted")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }

    scenario("Agent fails to de-authorise the relation with client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAndIsSubscribedAsAgent
      Await.result(repo.createRelationship(validTestRelationship.copy(relationshipStatus = Inactive)), 10 seconds)

      When("I call the deauth relationship endpoint")
      val deleteRelationshipResponse: WSResponse = Await.result(deleteRelationship(agentId, clientId, "INVALID"), 10 seconds)

      Then("I should get a 404 NotFound response")
      deleteRelationshipResponse.status shouldBe NOT_FOUND

      And("the relationship should remain unaffected")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId, Inactive)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }

    scenario("De-authorise all of client's agents, setting status to Inactive for all"){
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAsClient

      Given("there exists a relationship or more for particular clientId")
      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)
      Await.result(repo.createRelationship(validTestRelationship.copy(arn = Arn(agentId2))), 10 seconds)

      When("I call the de-auth relationship endpoint")
      val deleteRelationshipResponse: WSResponse = Await.result(deleteClientRelationships(clientId, service), 10 seconds)

      Then("I should get a 200 OK response")
      deleteRelationshipResponse.status shouldBe OK

      And("the relationship should be deleted")
      val agentRelationships: Future[List[Relationship]] = repo.findClientRelationships(service, clientId, Inactive)
      Await.result(agentRelationships, 10 seconds).length shouldBe 2
    }
  }


}
