package uk.gov.hmrc.agentfirelationship
import java.time.LocalDateTime
import javax.inject.Singleton
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.agentfirelationship.models.{DeletionCount, Relationship, TerminationResponse}
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.{Active, Terminated}
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentfirelationship.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport


import scala.concurrent.duration._
import language.postfixOps
import scala.concurrent.Await

@Singleton
class RemoveAgentRelationshipsISpec extends IntegrationSpec with UpstreamServicesStubs
  with RelationshipActions with GuiceOneServerPerSuite with CleanMongoCollectionSupport {


  def repo: RelationshipMongoRepository = app.injector.instanceOf[RelationshipMongoRepository]

  override implicit lazy val app: Application = appBuilder.build()
  override def arn = agentId
  override def nino = clientId

  val testResponseDate = LocalDateTime.now
  val validTestRelationship: Relationship = Relationship(Arn(arn), service, nino, Some(Active), testResponseDate, None)
  val validTestRelationshipTerminated: Relationship = Relationship(Arn(arn), service, clientId2, Some(Terminated), testResponseDate, None)

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships" -> false,
        "features.check-cesa-relationships" -> false)


  Feature("Remove Agent Relationships for given ARN")  {
    Scenario("Login as Stride User and removing relationships for given arn")  {

      Given("Agent Has Relationships")

      Await.result(repo.createRelationship(validTestRelationship), 10 seconds)
      Await.result(repo.createRelationship(validTestRelationshipTerminated), 10 seconds)

      And("Login as Stride")
      isLoggedInWithStride("caat")

      Await.result(repo.findRelationships(arn, service, clientId), 10 seconds).length shouldBe 1
      Await.result(repo.findRelationships(arn, service, clientId), 10 seconds).length shouldBe 1

      When("HMRC User calls the remove agent relationships by arn")
      val result = Await.result(removeAFIRelationshipsForAgent(agentId), 10 seconds)

      result.status shouldBe 200
      result.json shouldBe Json.toJson[TerminationResponse](TerminationResponse(Seq(DeletionCount("agent-fi-relationship", "fi-relationship", 2))))

      Then("the relationship is removed from mongo")
      Await.result(repo.findRelationships(arn, service, clientId), 10 seconds).length shouldBe 0
      Await.result(repo.findRelationships(arn, service, clientId), 10 seconds).length shouldBe 0

    }

    Scenario("Login with Basic Auth and removing relationships for given arn but return 0")  {
      Given("Agent Has No Relationships")

      And("Login as Stride")

      Await.result(repo.findRelationships(arn, service, clientId), 10 seconds).length shouldBe 0
      Await.result(repo.findRelationships(arn, service, clientId), 10 seconds).length shouldBe 0

      When("HMRC User calls the remove agent relationships by arn")
      val result = Await.result(removeAFIRelationshipsForAgent(agentId), 10 seconds)

      result.status shouldBe 200
      result.json shouldBe Json.toJson[TerminationResponse](TerminationResponse(Seq(DeletionCount("agent-fi-relationship", "fi-relationship", 0))))
    }

    Scenario("Login as Stride User and removing relationships but provided invalid ARN")  {
      Given("Login as Stride")

      Await.result(repo.findRelationships(arn, service, clientId), 10 seconds).length shouldBe 0
      Await.result(repo.findRelationships(arn, service, clientId), 10 seconds).length shouldBe 0

      When("HMRC User calls the remove agent relationships but with invalid ARN")
      val result = Await.result(removeAFIRelationshipsForAgent("MARN01"), 10 seconds)

      result.status shouldBe 400
    }
  }

}
