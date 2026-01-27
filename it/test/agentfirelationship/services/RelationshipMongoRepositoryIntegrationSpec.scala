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

package agentfirelationship.services

import java.time.LocalDateTime
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import agentfirelationship.agentId
import agentfirelationship.clientId
import agentfirelationship.service
import agentfirelationship.support.UpstreamServicesStubs
import org.mongodb.scala.model.Filters.{ equal => mongoEqual }
import org.mongodb.scala.model.Filters.and
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.Application
import uk.gov.hmrc.agentfirelationship.models.Arn
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Terminated
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentfirelationship.support.UnitSpec
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

@Singleton
class RelationshipMongoRepositoryIntegrationSpec
    extends UnitSpec
    with UpstreamServicesStubs
    with GuiceOneServerPerSuite
    with CleanMongoCollectionSupport {

  def repo: RelationshipMongoRepository = app.injector.instanceOf[RelationshipMongoRepository]

  implicit override lazy val app: Application = appBuilder.build()
  override def arn: String                    = agentId
  override def nino: String                   = clientId

  val now: LocalDateTime                          = LocalDateTime.now
  val testTerminatedRelationshipStartDate: String = now.minusDays(10).toString
  val testTerminatedRelationshipEndDate: String   = now.minusDays(6).toString
  val testActiveRelationshipStartDate: String     = LocalDateTime.now.minusDays(5).toString

  val activeTestRelationship: Relationship =
    Relationship(Arn(arn), service, nino, Some(Active), LocalDateTime.parse(testActiveRelationshipStartDate), None)

  val terminatedTestRelationship: Relationship = activeTestRelationship.copy(
    relationshipStatus = Some(Terminated),
    endDate = Some(LocalDateTime.parse(testTerminatedRelationshipEndDate))
  )

  val validTestRelationshipCesa: Relationship = Relationship(
    Arn(arn),
    service,
    nino,
    Some(Active),
    LocalDateTime.parse(testActiveRelationshipStartDate),
    None,
    fromCesa = Some(true)
  )

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.auth.port"   -> wireMockPort,
        "auditing.consumer.baseUri.port"    -> wireMockPort,
        "mongodb.uri"                       -> s"mongodb://127.0.0.1:27017/test-${this.getClass.getSimpleName}",
        "features.copy-cesa-relationships"  -> false,
        "features.check-cesa-relationships" -> false
      )

  def insertRawRelationship(
      clientId: String,
      status: RelationshipStatus = Active,
      startDate: LocalDateTime = LocalDateTime.parse(testActiveRelationshipStartDate)
  ): Unit =
    await(
      repo.collection
        .insertOne(
          Relationship(
            Arn(arn),
            service,
            clientId,
            Some(status),
            startDate,
            None
          )
        )
        .toFuture()
    )

  "RelationshipMongoRepository" should {
    "return active relationships for findRelationships" in {
      await(repo.createRelationship(activeTestRelationship))
      await(repo.createRelationship(validTestRelationshipCesa))
      await(repo.createRelationship(terminatedTestRelationship))

      await(
        repo.findAnyRelationships(
          activeTestRelationship.arn.value,
          validTestRelationshipCesa.service,
          terminatedTestRelationship.clientId
        )
      ).size shouldBe 3

      val result = await(repo.findRelationships(arn, service, nino, Active))

      result should not be empty
      result.head shouldBe activeTestRelationship
    }

    "return empty results if no active relationships found" in {
      await(repo.createRelationship(terminatedTestRelationship))
      val result = await(repo.findRelationships(arn, service, nino, Active))

      result shouldBe empty
    }

    "return TERMINATED relationships" in {
      await(repo.createRelationship(activeTestRelationship))
      await(repo.createRelationship(validTestRelationshipCesa))
      await(repo.createRelationship(terminatedTestRelationship))

      await(
        repo.findAnyRelationships(
          activeTestRelationship.arn.value,
          validTestRelationshipCesa.service,
          terminatedTestRelationship.clientId
        )
      ).size shouldBe 3

      val result = await(repo.findRelationships(arn, service, nino, Terminated))

      result should not be empty
      result.head shouldBe terminatedTestRelationship
    }

    "return empty results if no TERMINATED relationships found" in {
      await(repo.createRelationship(activeTestRelationship))
      val result = await(repo.findRelationships(arn, service, nino, Terminated))

      result shouldBe empty
    }

    "TERMINATE a relationship and set the end date to today" in {
      await(repo.createRelationship(terminatedTestRelationship))
      await(repo.createRelationship(activeTestRelationship))

      val result = await(repo.terminateRelationship(arn, service, nino))

      result shouldBe true

      await(repo.findClientRelationships(service, nino, Active)) shouldBe Seq.empty

      await(repo.findClientRelationships(service, nino, Terminated))
        .map(_.endDate.get.toLocalDate.toString) shouldBe Seq(
        now.minusDays(6).toLocalDate.toString,
        now.toLocalDate.toString
      )
    }

    "getDuplicateNinoWoSuffixRecords" should {

      "Find duplicate nino entries for 1 group of duplicates" in {
        insertRawRelationship("AB123456A")
        insertRawRelationship("AB123456B")
        insertRawRelationship("BA654321A")

        val result = await(repo.getDuplicateNinoWoSuffixRecords)

        result shouldBe 1
      }

      "Find duplicate nino entries for more than 1 group of duplicates" in {
        insertRawRelationship("AB123456A")
        insertRawRelationship("AB123456B")
        insertRawRelationship("AB123456C")
        insertRawRelationship("AB123456D")

        insertRawRelationship("BA654321A")
        insertRawRelationship("BA654321B")

        insertRawRelationship("CE987654B")

        val result = await(repo.getDuplicateNinoWoSuffixRecords)

        result shouldBe 2
      }

      "Find duplicate nino entries for only active relationships" in {
        insertRawRelationship("AB123456A", Active)
        insertRawRelationship("AB123456B", Active)
        insertRawRelationship("AB123456C", Active)
        insertRawRelationship("AB123456D", Active)

        insertRawRelationship("BA654321A", Active)
        insertRawRelationship("BA654321B", Terminated)

        insertRawRelationship("CE987654B", Active)

        val result = await(repo.getDuplicateNinoWoSuffixRecords)

        result shouldBe 1
      }

      "Find no duplicate nino entries if none exist" in {
        insertRawRelationship("AB123456A")
        insertRawRelationship("BA654321A")
        insertRawRelationship("CE987654B")

        val result = await(repo.getDuplicateNinoWoSuffixRecords)

        result shouldBe 0
      }
    }

    "getLastCreatedDuplicateNinoRecord" should {

      "Return latest record LocalDateTime" in {
        insertRawRelationship("AB123456A", startDate = LocalDateTime.parse("2023-01-01T00:00:00"))
        insertRawRelationship("AB123456A", startDate = LocalDateTime.parse("2025-01-01T00:00:00"))
        insertRawRelationship("AB123456A", startDate = LocalDateTime.parse("2026-01-01T00:00:00"))
        insertRawRelationship("AB123456A", startDate = LocalDateTime.parse("2024-01-01T00:00:00"))

        insertRawRelationship("BA654321A", startDate = LocalDateTime.parse("2023-01-01T00:00:00"))
        insertRawRelationship("BA654321A", startDate = LocalDateTime.parse("2025-01-22T12:00:00"))
        insertRawRelationship("BA654321A", startDate = LocalDateTime.parse("2026-01-23T12:00:00"))
        insertRawRelationship("BA654321A", startDate = LocalDateTime.parse("2024-01-23T00:00:00"))

        insertRawRelationship("CE987654B")

        val result = await(repo.getLastCreatedDuplicateNinoRecord)

        result shouldBe LocalDateTime.parse("2026-01-23T12:00:00")
      }

      "Not return a record which is not a duplicate" in {
        insertRawRelationship("AB123456A", startDate = LocalDateTime.parse("2023-01-01T00:00:00"))
        insertRawRelationship("AB123456A", startDate = LocalDateTime.parse("2025-01-01T00:00:00"))
        insertRawRelationship("AB123456A", startDate = LocalDateTime.parse("2026-01-01T00:00:00"))
        insertRawRelationship("AB123456A", startDate = LocalDateTime.parse("2024-01-01T00:00:00"))

        insertRawRelationship("BA654321A", startDate = LocalDateTime.parse("2023-01-01T00:00:00"))
        insertRawRelationship("BA654321A", startDate = LocalDateTime.parse("2025-01-22T12:00:00"))
        insertRawRelationship("BA654321A", startDate = LocalDateTime.parse("2026-01-23T12:00:00"))
        insertRawRelationship("BA654321A", startDate = LocalDateTime.parse("2024-01-23T00:00:00"))

        insertRawRelationship("BA654322A", startDate = LocalDateTime.parse("2026-01-26T00:00:00"))

        insertRawRelationship("CE987654B")

        val result = await(repo.getLastCreatedDuplicateNinoRecord)

        result shouldBe LocalDateTime.parse("2026-01-23T12:00:00")
      }
    }

    "findWithNinoSuffix" should {

      "retrieve records for a given nino" in {
        val recordWoNinoSuffix   = activeTestRelationship.copy(clientId = "AB123456")
        val recordWithNinoSuffix = activeTestRelationship.copy(clientId = "BA654321A")
        await(
          repo.collection
            .insertMany(
              Seq(
                recordWoNinoSuffix,
                recordWithNinoSuffix
              )
            )
            .toFuture()
        )
        val result: Seq[Relationship] = await(repo.findWithNinoSuffix.toFuture)
        result shouldBe Seq(
          activeTestRelationship.copy(clientId = "BA654321A"),
        )
      }

      "fail to retrieve records when none are found for the given nino" in {
        val recordWoNinoSuffix  = activeTestRelationship.copy(clientId = "AB123456")
        val recordWoNinoSuffix2 = activeTestRelationship.copy(clientId = "BA654321")
        await(
          repo.collection
            .insertMany(
              Seq(
                recordWoNinoSuffix,
                recordWoNinoSuffix2
              )
            )
            .toFuture()
        )
        await(repo.findWithNinoSuffix.toFuture) shouldBe Seq.empty
      }
    }

    "removeNinoSuffix" should {

      "remove suffix from nino for active record" in {
        val ninoWithSuffix    = "SX579189D"
        val ninoWithoutSuffix = "SX579189"

        val inserted = activeTestRelationship.copy(clientId = ninoWithSuffix)
        val arn      = inserted.arn
        val service  = inserted.service

        await(
          repo.collection
            .insertOne(inserted)
            .toFuture()
        )

        await(repo.collection.countDocuments().toFuture()) shouldBe 1

        await(
          repo.removeNinoSuffix(
            clientId = ninoWithSuffix
          )
        )

        val updatedOpt = await(
          repo.collection
            .find(
              and(
                mongoEqual("arn", arn.value),
                mongoEqual("service", service)
              )
            )
            .headOption()
        )

        val updated = updatedOpt.getOrElse(fail(s"Expected updated record not found for arn=$arn service=$service"))
        updated.clientId shouldBe ninoWithoutSuffix
      }

      "removeNinoSuffix should remove suffix from nino for inactive record" in {
        val ninoWithSuffix    = "SX579189D"
        val ninoWithoutSuffix = "SX579189"

        val inserted = activeTestRelationship.copy(clientId = ninoWithSuffix, relationshipStatus = Some(Terminated))
        val arn      = inserted.arn
        val service  = inserted.service

        await(
          repo.collection
            .insertOne(inserted)
            .toFuture()
        )

        await(repo.collection.countDocuments().toFuture()) shouldBe 1

        await(
          repo.removeNinoSuffix(
            clientId = ninoWithSuffix
          )
        )

        val updatedOpt = await(
          repo.collection
            .find(
              and(
                mongoEqual("arn", arn.value),
                mongoEqual("service", service)
              )
            )
            .headOption()
        )

        val updated = updatedOpt.getOrElse(fail(s"Expected updated record not found for arn=$arn service=$service"))
        updated.clientId shouldBe ninoWithoutSuffix
      }

      "removeNinoSuffix should not create extra documents" in {
        val ninoWithSuffix = "SX579189D"

        await(
          repo.collection
            .insertOne(
              activeTestRelationship.copy(clientId = ninoWithSuffix)
            )
            .toFuture()
        )

        await(
          repo.removeNinoSuffix(
            clientId = ninoWithSuffix
          )
        )

        await(repo.collection.countDocuments().toFuture()) shouldBe 1
      }

      "removeNinoSuffix should be idempotent when no suffix exists" in {
        val ninoWithoutSuffix = "SX579189"

        val inserted = activeTestRelationship.copy(clientId = ninoWithoutSuffix)
        val arn      = inserted.arn
        val service  = inserted.service

        await(
          repo.collection
            .insertOne(inserted)
            .toFuture()
        )

        await(
          repo.removeNinoSuffix(
            clientId = ninoWithoutSuffix
          )
        )

        val resultOpt = await(
          repo.collection
            .find(
              and(
                mongoEqual("arn", arn.value),
                mongoEqual("service", service)
              )
            )
            .headOption()
        )

        val result = resultOpt.getOrElse(fail(s"Expected record not found for arn=$arn service=$service"))
        result.clientId shouldBe ninoWithoutSuffix
        await(repo.collection.countDocuments().toFuture()) shouldBe 1
      }

      "removeNinoSuffix should return number of records updated for duplicated records" in {
        val ninoWithSuffix = "SX579189D"

        val relationships = Seq(
          activeTestRelationship.copy(clientId = ninoWithSuffix),
          activeTestRelationship.copy(clientId = ninoWithSuffix),
          activeTestRelationship.copy(clientId = ninoWithSuffix)
        )

        await(repo.collection.insertMany(relationships).toFuture())
        await(repo.collection.countDocuments().toFuture()) shouldBe 3

        val updatedCounts =
          await(
            Future.sequence(
              relationships.map { _ =>
                repo.removeNinoSuffix(
                  clientId = ninoWithSuffix
                )
              }
            )
          )

        updatedCounts.sum shouldBe 3
      }
    }
  }
}
