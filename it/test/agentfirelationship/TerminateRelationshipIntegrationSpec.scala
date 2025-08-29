/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package agentfirelationship

import java.time.LocalDateTime
import javax.inject.Singleton

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.Future
import scala.language.postfixOps

import agentfirelationship.support.IntegrationSpec
import agentfirelationship.support.RelationshipActions
import agentfirelationship.support.UpstreamServicesStubs
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSResponse
import play.api.Application
import uk.gov.hmrc.agentfirelationship.models.Arn
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

@Singleton
class TerminateRelationshipIntegrationSpec
    extends IntegrationSpec
    with UpstreamServicesStubs
    with RelationshipActions
    with GuiceOneServerPerSuite
    with CleanMongoCollectionSupport {

  def repo: RelationshipMongoRepository = app.injector.instanceOf[RelationshipMongoRepository]

  implicit override lazy val app: Application = appBuilder.build()
  override def arn: String                    = agentId
  override def nino: String                   = clientId

  val testResponseDate: LocalDateTime     = LocalDateTime.now
  val validTestRelationship: Relationship = Relationship(Arn(arn), service, nino, Some(Active), testResponseDate, None)

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"   -> wireMockPort,
        "auditing.consumer.baseUri.port"    -> wireMockPort,
        "mongodb.uri"                       -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships"  -> false,
        "features.check-cesa-relationships" -> false
      )

  Feature("Terminate a relationship between an agent and a client") {

    Scenario("Agent terminates an existing relationship with client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAndIsSubscribedAsAgent
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the terminates relationship endpoint")
      val terminateRelationshipResponse: WSResponse =
        Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I should get a 200 OK response")
      terminateRelationshipResponse.status shouldBe OK

      And("the relationship should be terminated")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }

    Scenario(
      "Agent terminates an existing relationship with client for a given service when the relationship is created for the second time"
    ) {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInAndIsSubscribedAsAgent
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED

      val agentRelationship: Relationship =
        Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds).head

      And("Confirm the relationship contains the start date")
      agentRelationship.relationshipStatus shouldBe Some(RelationshipStatus.Active)

      And("I delete the relationship")

      val deleteRelResponse = Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)
      deleteRelResponse.status shouldBe 200

      Await
        .result(repo.findAnyRelationships(agentId, service, clientId), 10 seconds)
        .head
        .relationshipStatus shouldBe Some(RelationshipStatus.Terminated)

      Then("I create the relationship for same parameters for second time")
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      And("I delete the newly created relationship")
      val secondDeleteResponse = Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)
      secondDeleteResponse.status shouldBe 200

      Await.result(repo.findAnyRelationships(agentId, service, clientId), 10 seconds).size shouldBe 2
      Await
        .result(repo.findAnyRelationships(agentId, service, clientId), 10 seconds)
        .last
        .relationshipStatus shouldBe Some(RelationshipStatus.Terminated)
    }

    Scenario("Agent fails to terminates the relation with client for an invalid service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAndIsSubscribedAsAgent
      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)

      When("I call the terminates relationship endpoint")
      val terminateRelationshipResponse: WSResponse =
        Await.result(terminateRelationship(agentId, clientId, "INVALID"), 10 seconds)

      Then("I should get a 404 NotFound response")
      terminateRelationshipResponse.status shouldBe NOT_FOUND

      And("the relationship should remain unaffected")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId, Active)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }

    Scenario("Agent fails to terminates the relation with client for an invalid agent id") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInAndIsSubscribedAsAgent
      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)

      When("I call the terminates relationship endpoint")
      val terminateRelationshipResponse: WSResponse =
        Await.result(terminateRelationship(agentId2, clientId, service), 10 seconds)

      Then("I should get a 403 Forbidden response")
      terminateRelationshipResponse.status shouldBe FORBIDDEN
    }

    Scenario("The user is not logged in with GG credentials") {
      isNotLoggedIn
      When("I call the terminates relationship endpoint")
      val terminateRelationshipResponse: WSResponse =
        Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 401 response ")
      terminateRelationshipResponse.status shouldBe UNAUTHORIZED
    }

    Scenario("The user does not have an affinity group") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the terminates relationship endpoint")
      isLoggedInWithoutAffinityGroup
      val terminateRelationshipResponse: WSResponse =
        Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      terminateRelationshipResponse.status shouldBe FORBIDDEN
    }

    Scenario("The user has invalid enrolments") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the terminates relationship endpoint")
      isLoggedInWithInvalidEnrolments
      val terminateRelationshipResponse: WSResponse =
        Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      terminateRelationshipResponse.status shouldBe FORBIDDEN
    }

    Scenario("Stride user terminates an existing relationship with client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInWithStride("maintain agent relationships")
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the terminate relationship endpoint")
      val terminateRelationshipResponse: WSResponse =
        Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I should get a 200 OK response")
      terminateRelationshipResponse.status shouldBe OK

      And("the relationship should be terminated")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }

    Scenario("Stride user (new format) terminates an existing relationship with client for a given service") {

      Given("there exists a relationship between an agent and client for a given service")
      givenCreatedAuditEventStub(auditDetails)
      givenEndedAuditEventStub(auditDetails)
      isLoggedInWithStride("maintain_agent_relationships")
      Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      When("I call the terminate relationship endpoint")
      val terminateRelationshipResponse: WSResponse =
        Await.result(terminateRelationship(agentId, clientId, service), 10 seconds)

      Then("I should get a 200 OK response")
      terminateRelationshipResponse.status shouldBe OK

      And("the relationship should be terminated")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }
  }
}
