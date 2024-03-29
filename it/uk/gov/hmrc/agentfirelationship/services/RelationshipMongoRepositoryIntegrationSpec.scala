package uk.gov.hmrc.agentfirelationship.services

import java.time.LocalDateTime
import javax.inject.Singleton
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.{Active, Terminated}
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentfirelationship.{agentId, clientId, service}
import uk.gov.hmrc.agentfirelationship.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentfirelationship.support.UnitSpec
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport



@Singleton
class RelationshipMongoRepositoryIntegrationSpec extends UnitSpec
  with UpstreamServicesStubs with GuiceOneServerPerSuite with CleanMongoCollectionSupport {


  def repo: RelationshipMongoRepository = app.injector.instanceOf[RelationshipMongoRepository]

  override implicit lazy val app: Application = appBuilder.build()
  override def arn = agentId
  override def nino = clientId

  val testResponseDate: String = LocalDateTime.now.toString
  val validTestRelationship: Relationship = Relationship(Arn(arn), service, nino, Some(Active), LocalDateTime.parse(testResponseDate), None)
  val invalidTestRelationship: Relationship = validTestRelationship.copy(relationshipStatus = Some(Terminated))
  val validTestRelationshipCesa: Relationship = Relationship(Arn(arn), service, nino, Some(Active), LocalDateTime.parse(testResponseDate), None, fromCesa = Some(true))

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port" -> wireMockPort,
        "auditing.consumer.baseUri.port" -> wireMockPort,
        "mongodb.uri" -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships" -> false,
        "features.check-cesa-relationships" -> false)

  "RelationshipMongoRepository" should {
    "return active relationships for findRelationships" in {
      await(repo.createRelationship(validTestRelationship))
      await(repo.createRelationship(validTestRelationshipCesa))
      await(repo.createRelationship(invalidTestRelationship))

      await(repo.findAnyRelationships(validTestRelationship.arn.value,validTestRelationshipCesa.service,invalidTestRelationship.clientId)).size shouldBe 3

      val result = await(repo.findRelationships(arn, service, nino, Active))

      result should not be empty
      result.head shouldBe validTestRelationship
    }

    "return empty results if no active relationships found" in {
      await(repo.createRelationship(invalidTestRelationship))
      val result = await(repo.findRelationships(arn, service, nino, Active))

      result shouldBe empty
    }

    "return TERMINATED relationships" in {
      await(repo.createRelationship(validTestRelationship))
      await(repo.createRelationship(validTestRelationshipCesa))
      await(repo.createRelationship(invalidTestRelationship))

      await(repo.findAnyRelationships(validTestRelationship.arn.value,validTestRelationshipCesa.service,invalidTestRelationship.clientId)).size shouldBe 3

      val result = await(repo.findRelationships(arn, service, nino, Terminated))

      result should not be empty
      result.head shouldBe invalidTestRelationship
    }

    "return empty results if no TERMINATED relationships found" in {
      await(repo.createRelationship(validTestRelationship))
      val result = await(repo.findRelationships(arn, service, nino, Terminated))

      result shouldBe empty
    }
  }
}