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

import javax.inject.Inject

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

import agentfirelationship.support.IntegrationSpec
import agentfirelationship.support.RelationshipActions
import agentfirelationship.support.UpstreamServicesStubs
import com.google.inject.AbstractModule
import com.google.inject.Singleton
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.Application
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.models.Arn
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.mongo.MongoComponent

@Singleton
class TestRelationshipMongoRepository @Inject() (mongoComponent: MongoComponent, appConfig: AppConfig)
    extends RelationshipMongoRepository(appConfig, mongoComponent) {

  override def createRelationship(relationship: Relationship): Future[Unit] = {
    Future.failed(new Exception("Test mongodb failure"))
  }
}

class ViewRelationshipWhenMongoFailsIntegrationSpec
    extends IntegrationSpec
    with UpstreamServicesStubs
    with GuiceOneServerPerSuite
    with RelationshipActions
    with CleanMongoCollectionSupport {

  def repo: RelationshipMongoRepository = app.injector.instanceOf[RelationshipMongoRepository]

  override def arn = agentId

  override def nino = clientId

  implicit override lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"          -> wireMockPort,
        "auditing.consumer.baseUri.port"           -> wireMockPort,
        "microservice.services.des.port"           -> wireMockPort,
        "microservice.services.agent-mapping.port" -> wireMockPort,
        "mongodb.uri"                              -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships"         -> true,
        "features.check-cesa-relationships"        -> true
      )
      .overrides(new AbstractModule {
        override def configure(): Unit = {
          bind(classOf[RelationshipMongoRepository]).to(classOf[TestRelationshipMongoRepository])
          ()
        }
      })

  Feature("View relationships for a client individual") {

    Scenario("Agent views a non-existent relationship") {
      givenUserAuthorised()
      Given("no relationship exists for a combination of agent, client and service")
      Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds) shouldBe empty
      givenClientHasNoActiveRelationshipWithAgentInCESA(Nino(clientId))

      When("I call the View Relationship endpoint")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 404 NOT FOUND response")
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }

    Scenario("Agent views a relationship existing only in CESA") {
      givenUserAuthorised()
      Given("relationship exists in CESA and has been mapped for a combination of agent and client")
      Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds) shouldBe empty
      givenClientHasRelationshipWithAgentInCESA(Nino(clientId), "foo")
      givenArnIsKnownFor(Arn(agentId), SaAgentReference("foo"))
      givenCesaCopyAuditEventStub(
        Map(
          "agentReferenceNumber" -> agentId,
          "saAgentRef"           -> "foo",
          "service"              -> "personal-income-record",
          "clientId"             -> clientId,
          "clientIdType"         -> "ni"
        )
      )

      When("I call the View Relationship endpoint")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 200 OK response")
      viewRelationshipResponse.status shouldBe OK
      Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds) shouldBe empty

      And("The response body will not contain the relationship details")
      val jsonResponse = Json.parse(viewRelationshipResponse.body)
      jsonResponse shouldBe JsArray()
    }
  }
}
