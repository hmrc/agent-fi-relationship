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

import javax.inject.Inject

import com.google.inject.Singleton
import play.api.Logger
import play.api.libs.json.Format
import play.api.libs.json.Json.{format, toJsFieldJsValueWrapper}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RelationshipMongoService @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Relationship, String]("fi-relationship", mongoComponent.mongoConnector.db, format[Relationship], implicitly[Format[String]]) {
  //This is a temperary measure to update the current users in prod to the new value of service we will need a jira to delete it
  // After it is first used
  def dataMigrationAfi()(implicit ec: ExecutionContext): Future[Unit] = {
    val query = BSONDocument(
      "service" → "PAYE"
    )
    val update = BSONDocument("$set" -> BSONDocument(
      "service" → "afi"
    ))
    collection.update(query, update, multi = true).map(wr => {
      Logger.info("number of relationships updated from PAYE to afi " + wr.n)
    })
  }

  dataMigrationAfi()(ExecutionContext.global)

  def findRelationships(relationship: Relationship)(implicit ec: ExecutionContext): Future[List[Relationship]] =
    find(Seq(
      "arn" -> Some(relationship.arn.value),
      "service" -> Some(relationship.service),
      "clientId" -> Some(relationship.clientId))
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get)): _*)

  def createRelationship(relationship: Relationship)(implicit ec: ExecutionContext): Future[Unit] = insert(relationship).map(_ => ())

  def deleteRelationship(relationship: Relationship)(implicit ec: ExecutionContext): Future[Boolean] =
    remove("arn" -> Some(relationship.arn.value),
      "service" -> Some(relationship.service),
      "clientId" -> Some(relationship.clientId))
      .map(result => if (result.n == 0) false else result.ok)

  def findAllRelationshipsForAgent(arn: String)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    val searchOptions = Seq("arn" -> Some(arn))
      .filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))
    find(searchOptions: _*)
  }
}
