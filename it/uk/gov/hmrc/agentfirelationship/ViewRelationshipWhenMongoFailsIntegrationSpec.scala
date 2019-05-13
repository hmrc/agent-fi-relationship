package uk.gov.hmrc.agentfirelationship

import javax.inject.Inject

import com.google.inject.{ AbstractModule, Singleton }
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentfirelationship.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.{ Nino, SaAgentReference }

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import language.postfixOps

@Singleton
class TestRelationshipMongoService @Inject() (mongoComponent: ReactiveMongoComponent)
  extends RelationshipMongoService(30 days, mongoComponent) {

  override def createRelationship(relationship: Relationship)(implicit ec: ExecutionContext): Future[Unit] = {
    Future failed new Exception("Test mongodb failure")
  }

}

class ViewRelationshipWhenMongoFailsIntegrationSpec extends IntegrationSpec with UpstreamServicesStubs
  with GuiceOneServerPerSuite with RelationshipActions with MongoApp {
  me: DualSuite =>

  def repo: RelationshipMongoService = app.injector.instanceOf[RelationshipMongoService]

  override def arn = agentId

  override def nino = clientId

  override implicit lazy val app: Application = appBuilder.build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "microservice.services.des.port" -> wireMockPort,
        "microservice.services.agent-mapping.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships" -> true,
        "features.check-cesa-relationships" -> true)
      .overrides(new AbstractModule {
        override def configure(): Unit = {
          bind(classOf[RelationshipMongoService]).to(classOf[TestRelationshipMongoService])
        }
      })

  feature("View relationships for a client individual") {

    scenario("Agent views a non-existent relationship") {

      Given("no relationship exists for a combination of agent, client and service")
      Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds) shouldBe empty
      givenClientHasNoActiveRelationshipWithAgentInCESA(Nino(clientId))

      When("I call the View Relationship endpoint")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 404 NOT FOUND response")
      viewRelationshipResponse.status shouldBe NOT_FOUND
    }

    scenario("Agent views a relationship existing only in CESA") {

      Given("relationship exists in CESA and has been mapped for a combination of agent and client")
      Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds) shouldBe empty
      givenClientHasRelationshipWithAgentInCESA(Nino(clientId), "foo")
      givenArnIsKnownFor(Arn(agentId), SaAgentReference("foo"))
      givenCesaCopyAuditEventStub(Map(
        "agentReferenceNumber" -> agentId,
        "saAgentRef" -> "foo",
        "service" -> "personal-income-record",
        "clientId" -> clientId,
        "clientIdType" -> "ni"))

      When("I call the View Relationship endpoint")
      val viewRelationshipResponse: WSResponse = Await.result(getRelationship(agentId, clientId, service), 10 seconds)

      Then("I will receive a 200 OK response")
      viewRelationshipResponse.status shouldBe OK
      Await.result(repo.findRelationships(agentId, service, clientId), 10 seconds) shouldBe empty

      And("The response body will not contain the relationship details")
      val jsonResponse = Json.parse(viewRelationshipResponse.body)
      val actualAgentId = (jsonResponse(0) \ "arn").asOpt[String]
      val actualClientId = (jsonResponse(0) \ "clientId").asOpt[String]
      val actualService = (jsonResponse(0) \ "service").asOpt[String]
      actualAgentId shouldBe None
      actualClientId shouldBe None
      actualService shouldBe None
    }
  }
}
