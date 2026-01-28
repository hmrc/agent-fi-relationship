/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.repository

import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.google.inject.Singleton
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.BsonArray
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model._
import org.mongodb.scala.model.Accumulators._
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.combine
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.Document
import play.api.Logging
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.models.NinoWithoutSuffix
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.MongoComponent

@Singleton
class RelationshipMongoRepository @Inject() (appConfig: AppConfig, mongoComponent: MongoComponent)(
    implicit ec: ExecutionContext
) extends PlayMongoRepository[Relationship](
      mongoComponent = mongoComponent,
      collectionName = "fi-relationship",
      domainFormat = Relationship.relationshipFormat,
      extraCodecs = Codecs
        .playFormatCodecsBuilder(RelationshipStatus.relationshipStatusFormat)
        .forType[RelationshipStatus.Active.type]
        .forType[RelationshipStatus.Terminated.type]
        .build,
      indexes = Seq(
        IndexModel(
          ascending("arn", "service", "clientId", "relationshipStatus"),
          IndexOptions().name("Arn_Service_ClientId_RelationshipStatus")
        ),
        IndexModel(
          ascending("clientId", "relationshipStatus"),
          IndexOptions().name("ClientId_RelationshipStatus")
        ),
        IndexModel(
          ascending("arn", "relationshipStatus"),
          IndexOptions().name("Arn_RelationshipStatus")
        ),
        IndexModel(
          ascending("service", "clientId", "relationshipStatus"),
          IndexOptions().name("Service_ClientId_RelationshipStatus")
        ),
        IndexModel(
          ascending("arn", "service", "clientId"),
          IndexOptions().name("Arn_Service")
        ),
        IndexModel(
          ascending("service", "clientId"),
          IndexOptions().name("Service_ClientId")
        ),
        IndexModel(
          ascending("relationshipStatus", "clientId"),
          IndexOptions().name("RelationshipStatus_ClientId")
        ),
      )
    )
    with Logging {

  def findRelationships(
      arn: String,
      service: String,
      clientId: String,
      status: RelationshipStatus = Active
  ): Future[List[Relationship]] =
    collection
      .find(
        and(
          equal("arn", arn),
          equal("service", service),
          in("clientId", NinoWithoutSuffix(clientId).variations: _*),
          equal("relationshipStatus", status)
        )
      )
      .toFuture()
      .map(_.toList)

  def findAnyRelationships(arn: String, service: String, clientId: String): Future[Seq[Relationship]] =
    collection
      .find(
        and(equal("arn", arn), equal("service", service), in("clientId", NinoWithoutSuffix(clientId).variations: _*))
      )
      .toFuture()

  def createRelationship(relationship: Relationship): Future[Unit] =
    collection
      .insertOne(relationship.copy(clientId = NinoWithoutSuffix(relationship.clientId).value))
      .toFuture()
      .map(_ => ())

  def terminateRelationship(arn: String, service: String, clientId: String): Future[Boolean] =
    updateStatusToTerminated(
      and(
        equal("arn", arn),
        equal("service", service),
        in("clientId", NinoWithoutSuffix(clientId).variations: _*),
        equal("relationshipStatus", Active)
      )
    )

  def findClientRelationships(
      service: String,
      clientId: String,
      status: RelationshipStatus = Active
  ): Future[Seq[Relationship]] =
    collection
      .find(
        and(
          equal("service", service),
          in("clientId", NinoWithoutSuffix(clientId).variations: _*),
          equal("relationshipStatus", status)
        )
      )
      .toFuture()

  def findInactiveAgentRelationships(arn: String): Future[Seq[Relationship]] = {
    val from = LocalDateTime.now().minusDays(appConfig.inactiveRelationshipsShowLastDays.toDays.toInt)
    collection
      .find(and(equal("arn", arn), equal("relationshipStatus", "TERMINATED")))
      .toFuture()
      .map(_.filter(_.startDate.isAfter(from)))
  }

  def findActiveAgentRelationships(arn: String): Future[Seq[Relationship]] =
    collection
      .find(and(equal("arn", arn), equal("relationshipStatus", "ACTIVE")))
      .toFuture()

  def findInactiveClientRelationships(clientId: String): Future[Seq[Relationship]] =
    collection
      .find(and(in("clientId", NinoWithoutSuffix(clientId).variations: _*), equal("relationshipStatus", "TERMINATED")))
      .toFuture()

  def findActiveClientRelationships(clientId: String): Future[Seq[Relationship]] =
    collection
      .find(and(in("clientId", NinoWithoutSuffix(clientId).variations: _*), equal("relationshipStatus", "ACTIVE")))
      .toFuture()

  def terminateAgentRelationship(arn: String): Future[Seq[Int]] =
    collection.deleteMany(equal("arn", arn)).map(_.getDeletedCount.toInt).toFuture()

  private def updateStatusToTerminated(selector: Bson)(implicit ec: ExecutionContext): Future[Boolean] =
    collection
      .updateMany(
        selector,
        combine(
          set("relationshipStatus", RelationshipStatus.Terminated.key),
          set("endDate", LocalDateTime.now(ZoneId.of("UTC")).toString)
        )
      )
      .toFuture()
      .map { result =>
        if (result.getModifiedCount < 1) false
        else true
      }

  def getAllDuplicateNinoRecords: Future[Int] = {
    val pipeline: Seq[Bson] = Seq(
      `match`(equal("relationshipStatus", RelationshipStatus.Active.key)),
      group("$clientId", sum("count", 1)),
      `match`(gt("count", 1)),
      count("duplicateCount")
    )

    collection
      .aggregate[Document](pipeline)
      .map(doc => doc.getInteger("duplicateCount", 0))
      .headOption()
      .map(_.getOrElse(0))
  }

  def getDuplicateNinoWoSuffixRecords: Future[Int] = {
    val pipeline: Seq[Bson] = Seq(
      `match`(equal("relationshipStatus", RelationshipStatus.Active.key)),
      group(
        Document(
          "_id" -> Document(
            "$substrBytes" -> BsonArray(
              "$clientId",
              0,
              8
            )
          )
        ),
        sum("count", 1)
      ),
      `match`(gt("count", 1)),
      count("duplicateCount")
    )

    collection
      .aggregate[Document](pipeline)
      .map(doc => doc.getInteger("duplicateCount", 0))
      .headOption()
      .map(_.getOrElse(0))
  }

  def getLastCreatedDuplicateNinoRecord: Future[LocalDateTime] = {
    val pipeline: Seq[Bson] = Seq(
      `match`(equal("relationshipStatus", RelationshipStatus.Active.key)),
      group(
        "$clientId",
        sum("count", 1),
        max("latestStartDate", "$startDate")
      ),
      `match`(gt("count", 1)),
      group(
        null,
        max("overallLatestStartDate", "$latestStartDate")
      ),
      project(
        BsonDocument("overallLatestStartDate" -> 1, "_id" -> 0)
      )
    )

    collection
      .aggregate[Document](pipeline)
      .headOption()
      .map {
        _.map { doc =>
          LocalDateTime.parse(doc.getString("overallLatestStartDate"))
        }.get
      }
  }

  def removeNinoSuffixBulk(): Future[Long] = {
    collection
      .updateMany(
        expr(
          BsonDocument(
            "$gt" -> BsonArray(
              BsonDocument("$strLenBytes" -> "$clientId"),
              8
            )
          )
        ),
        Seq(
          BsonDocument(
            "$set" -> BsonDocument(
              "clientId" -> BsonDocument(
                "$substrBytes" -> BsonArray("$clientId", 0, 8)
              )
            )
          )
        )
      )
      .toFuture()
      .map(_.getModifiedCount)
  }
}
