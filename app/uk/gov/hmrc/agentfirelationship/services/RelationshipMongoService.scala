/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.services

import java.time.{LocalDateTime, ZoneId}
import javax.inject.Inject

import com.google.inject.Singleton
import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.models.{Relationship, RelationshipStatus}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RelationshipMongoService @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Relationship, BSONObjectID]("fi-relationship",
    mongoComponent.mongoConnector.db,
    Relationship.relationshipFormat,
    ReactiveMongoFormats.objectIdFormats) {

  //APB-1829 - This needs to be removed after all records have been updated. Currently we know there is <1000 records so it should be fine. But perhaps there would be overload if it is left in production.
  def addActiveRelationshipStatus()(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.update(
      BSONDocument("service" -> "afi","relationshipStatus" -> BSONDocument("$exists" -> false)),
      BSONDocument("$set" -> BSONDocument("relationshipStatus" -> RelationshipStatus.Active.key)),
      multi = true
    ).map { result =>
      result.writeErrors.foreach(error => Logger.warn(s"Updating relationships to have status have failed, error: $error"))
      if (result.nModified > 0) true else false

    }
  }

  //APB-1871 - This should also be removed once data migration has occurred
  def migrateFromAfi()(implicit ec: ExecutionContext): Future[Boolean] = {
    collection.update(
      BSONDocument("service" -> "afi"),
      BSONDocument("$set" -> BSONDocument("service" -> "PERSONAL-INCOME-RECORD")),
      multi = true
    ).map { result =>
      result.writeErrors.foreach(error => Logger.warn(s"Migrating relationships from afi to PERSONAL-INCOME-RECORD failed, error: $error"))
      if (result.nModified > 0) true else false
    }
  }

  def findRelationships(arn: String, service: String, clientId: String, status: RelationshipStatus = Active)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    find("arn" -> arn,
      "service" -> service,
      "clientId" -> clientId,
      "relationshipStatus" -> status)
  }

  def findAnyRelationships(arn: String, service: String, clientId: String)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    find(
      "arn" -> arn,
      "service" -> service,
      "clientId" -> clientId)
  }

  def createRelationship(relationship: Relationship)(implicit ec: ExecutionContext): Future[Unit] = {
    insert(relationship).map(_ => ())
  }

  def terminateRelationship(arn: String, service: String, clientId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    updateStatusToTerminated(BSONDocument("arn" -> arn, "service" -> service, "clientId" -> clientId))(false, ec)
  }

  def findClientRelationships(service: String, clientId: String, status: RelationshipStatus = Active)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    find("service" -> service,
      "clientId" -> clientId,
      "relationshipStatus" -> status)
  }

  def deleteAllClientIdRelationships(service: String, clientId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    updateStatusToTerminated(BSONDocument("service" -> service, "clientId" -> clientId))(true, ec)
  }

  private def updateStatusToTerminated(selector: BSONDocument)(implicit multi: Boolean = false, ec: ExecutionContext): Future[Boolean] = {
    collection.update(
      selector,
      BSONDocument("$set" -> BSONDocument("relationshipStatus" -> RelationshipStatus.Terminated.key,
        "endDate" -> LocalDateTime.now(ZoneId.of("UTC")).toString)),
      multi = multi
    ).map { result =>
      result.writeErrors.foreach(error => Logger.warn(s"Updating Relationship status to TERMINATED for ${selector.elements.mkString} failed: $error"))
      if (result.nModified > 0) true else false
    }
  }
}
