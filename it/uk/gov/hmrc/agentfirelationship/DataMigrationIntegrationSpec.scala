package uk.gov.hmrc.agentfirelationship

import org.scalatest.concurrent.Eventually
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentfirelationship.support.{IntegrationSpec, RelationshipActions}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class DataMigrationIntegrationSpec extends IntegrationSpec with RelationshipActions  with GuiceOneServerPerSuite  with Eventually{

  val mongo: RelationshipMongoService = app.injector.instanceOf[RelationshipMongoService]
  feature("A data Migration has been requested") {

    scenario("A relationship has the service name PAYE") {

      Given("agent has a relationship with the name PAYE")
      val agentId = "Agent12345"
      val client1Id = "Client12345"
      val service = "PAYE"
      val relationship = Relationship(Arn(agentId), service, client1Id)
      createRelationship(agentId, client1Id, service)
      When("I call the dataMigrationAfi ")

       mongo.dataMigrationAfi()
      Then("The relationship will have it's service changed to afi")
      eventually {
        val agentRelationships: Future[List[Relationship]] = mongo.findRelationships(relationship)
        val agentRelationshipsResult: Seq[Relationship] = Await.result(agentRelationships, 10000 millis)
        agentRelationshipsResult.length shouldBe agentRelationshipsResult.count(_.service == "afi")
      }

      deleteRelationship(agentId, client1Id, service)
    }
  }
}