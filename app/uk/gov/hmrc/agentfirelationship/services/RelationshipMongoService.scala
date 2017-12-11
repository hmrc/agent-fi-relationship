/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.agentfirelationship.models.{Relationship, RelationshipStatus}
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RelationshipMongoService @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Relationship, BSONObjectID]("fi-relationship",
    mongoComponent.mongoConnector.db,
    Relationship.relationshipFormat,
    ReactiveMongoFormats.objectIdFormats) with AtomicUpdate[Relationship] {

  def findRelationships(arn: String, service: String, clientId: String, status: RelationshipStatus = Active)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    find("arn" -> arn,
      "service" -> service,
      "clientId" -> clientId,
      "relationshipStatus" -> status)
  }

  def findCESARelationships(arn: String, service: String, clientId: String, status: RelationshipStatus = Active)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    find(
      "arn" -> arn,
      "service" -> service,
      "clientId" -> clientId,
      "relationshipStatus" -> status,
      "fromCesa" -> "true") // FIXME change to status check
  }

  def createRelationship(relationship: Relationship)(implicit ec: ExecutionContext): Future[Unit] = {
      insert(relationship).map(_ => ())
  }

  def deauthoriseRelationship(arn: String, service: String, clientId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    atomicUpdate(
      finder = BSONDocument("arn" -> arn, "service" -> service, "clientId" -> clientId),
      modifierBson = BSONDocument("$set" -> BSONDocument("relationshipStatus" -> RelationshipStatus.Inactive.key,
        "endDate" -> LocalDateTime.now(ZoneId.of("UTC")).toString))).map(_.map { update =>
      update.writeResult.errMsg.foreach(error => Logger.warn(s"Updating Relationship status to Inactive for arn:" +
        s" $arn, clientId: $clientId service: $service failed: $error"))
      update.writeResult.ok
    }.getOrElse(false))
  }

  def findClientRelationships(service: String, clientId: String, status: RelationshipStatus = Active)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    find("service" -> service,
      "clientId" -> clientId,
      "relationshipStatus" -> status)
  }

  def deleteAllClientIdRelationships(service: String, clientId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    remove(
      "service" -> service,
      "clientId" -> clientId)
      .map(result => if (result.n == 0) false else result.ok)
  }

  override def isInsertion(newRecord: BSONObjectID, oldRecord: Relationship): Boolean = false
}
