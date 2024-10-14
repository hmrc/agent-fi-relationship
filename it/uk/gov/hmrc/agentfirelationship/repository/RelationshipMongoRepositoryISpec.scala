package uk.gov.hmrc.agentfirelationship.repository

import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Terminated
import uk.gov.hmrc.agentfirelationship.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime

class RelationshipMongoRepositoryISpec extends UnitSpec with Eventually with Inside with DefaultPlayMongoRepositorySupport[Relationship] {
  def repository: RelationshipMongoRepository = app.injector.instanceOf[RelationshipMongoRepository]

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure("mongodb.uri" -> mongoUri)

  implicit lazy val app: Application = appBuilder.build()

  "findDuplicateInvitations" should {
    "return Seq[Relationship]" in {
      val irvrRelationship = Relationship.createNew(
        Arn("BARN0190149"),
        "PERSONAL-INCOME-RECORD",
        "AB000001D",
        Some(Terminated),
        LocalDateTime.now(),
        Some(LocalDateTime.now()),
        None
      )
      val irvrRelationship2 = Relationship.createNew(
        Arn("BARN0190149"),
        "PERSONAL-INCOME-RECORD",
        "AB000001D",
        Some(Terminated),
        LocalDateTime.now(),
        Some(LocalDateTime.now()),
        None
      )
      await(repository.collection.insertOne(irvrRelationship).toFuture())
      await(repository.collection.insertOne(irvrRelationship2).toFuture())
      val result: Int = await(repository.findMultipleDeauthorisationsForIRV())

      result shouldBe 2

    }
  }
}
