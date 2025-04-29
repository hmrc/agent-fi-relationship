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
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Terminated
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

@Singleton
class CreateRelationshipIntegrationSpec
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

  Feature("Create a relationship between an agent and an individual as an agent") {

    info("I want to create a relationship with a client individual for a specific service")
    info("So that I am permitted to view the client's service/regime data")

    Scenario("Create a new relationship with simple values") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInAndIsSubscribedAsAgent
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 CREATED response")
      createRelationshipResponse.status shouldBe CREATED

      val agentRelationship: Relationship =
        Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds).head

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
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

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
      val createRelationshipResponse1: WSResponse =
        Await.result(createRelationship(agentId2, clientId, service, testResponseDate), 10 seconds)
      Then("I will receive a 201 response ")
      createRelationshipResponse1.status shouldBe CREATED

      And("the new relationship should be created")
      val agentRelationships1: Future[List[Relationship]] = repo.findRelationships(agentId2, service, clientId)
      Await.result(agentRelationships1, 10 seconds).length shouldBe 1

      isLoggedInAndIsSubscribedAsAgent

      When("I call the create-relationship for same service for agent 1")
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

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
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 401 response ")
      createRelationshipResponse.status shouldBe UNAUTHORIZED
    }

    Scenario("The user does not have an affinity group") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInWithoutAffinityGroup
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 403 FORBIDDEN response")
      createRelationshipResponse.status shouldBe FORBIDDEN
    }

    Scenario("The user has invalid enrolments") {

      Given("a create-relationship request with basic string values for Agent ID, client ID and service")
      givenCreatedAuditEventStub(auditDetails)

      When("I call the create-relationship endpoint")
      isLoggedInWithInvalidEnrolments
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

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
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

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
      val createRelationshipResponse: WSResponse =
        Await.result(createRelationship(agentId, clientId, service, testResponseDate), 10 seconds)

      Then("I will receive a 201 response ")
      createRelationshipResponse.status shouldBe CREATED

      And("the new relationship should not be created")
      val agentRelationships: Future[List[Relationship]] = repo.findRelationships(agentId, service, clientId)
      Await.result(agentRelationships, 10 seconds).length shouldBe 1
    }
  }
}
