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
    extends ReactiveRepository[Relationship, BSONObjectID](
      "fi-relationship",
      mongoComponent.mongoConnector.db,
      Relationship.relationshipFormat,
      ReactiveMongoFormats.objectIdFormats) {

  def findRelationships(arn: String, service: String, clientId: String, status: RelationshipStatus = Active)(
    implicit ec: ExecutionContext): Future[List[Relationship]] =
    find("arn" -> arn, "service" -> service, "clientId" -> clientId.replaceAll(" ", ""), "relationshipStatus" -> status)

  def findAnyRelationships(arn: String, service: String, clientId: String)(
    implicit ec: ExecutionContext): Future[List[Relationship]] =
    find("arn" -> arn, "service" -> service, "clientId" -> clientId.replaceAll(" ", ""))

  def createRelationship(relationship: Relationship)(implicit ec: ExecutionContext): Future[Unit] =
    insert(relationship.copy(clientId = relationship.clientId.replaceAll(" ", ""))).map(_ => ())

  def terminateRelationship(arn: String, service: String, clientId: String)(
    implicit ec: ExecutionContext): Future[Boolean] =
    updateStatusToTerminated(
      BSONDocument("arn" -> arn, "service" -> service, "clientId" -> clientId.replaceAll(" ", "")))(true, ec)

  def findClientRelationships(service: String, clientId: String, status: RelationshipStatus = Active)(
    implicit ec: ExecutionContext): Future[List[Relationship]] =
    find("service" -> service, "clientId" -> clientId.replaceAll(" ", ""), "relationshipStatus" -> status)

  def deleteAllClientIdRelationships(service: String, clientId: String)(
    implicit ec: ExecutionContext): Future[Boolean] =
    updateStatusToTerminated(BSONDocument("service" -> service, "clientId" -> clientId.replaceAll(" ", "")))(true, ec)

  def findInActiveAgentRelationships(arn: String)(implicit ec: ExecutionContext): Future[List[Relationship]] =
    find("arn" -> arn, "relationshipStatus" -> "TERMINATED")

  private def updateStatusToTerminated(
    selector: BSONDocument)(implicit multi: Boolean = false, ec: ExecutionContext): Future[Boolean] =
    collection
      .update(
        selector,
        BSONDocument(
          "$set" -> BSONDocument(
            "relationshipStatus" -> RelationshipStatus.Terminated.key,
            "endDate"            -> LocalDateTime.now(ZoneId.of("UTC")).toString)),
        multi = multi
      )
      .map { result =>
        result.writeErrors.foreach(error =>
          Logger.warn(s"Updating Relationship status to TERMINATED for ${selector.elements.mkString} failed: $error"))
        if (result.nModified > 0) true else false
      }
}
