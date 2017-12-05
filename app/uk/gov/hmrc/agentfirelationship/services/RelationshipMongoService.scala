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
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RelationshipMongoService @Inject()(mongoComponent: ReactiveMongoComponent)
  extends ReactiveRepository[Relationship, String]("fi-relationship", mongoComponent.mongoConnector.db, format[Relationship], implicitly[Format[String]]) {

  def findRelationships(arn: String, service: String, clientId: String)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
      find(Seq(
        "arn" -> arn,
        "service" -> service,
        "clientId" -> clientId)
        .map(option => option._1 -> toJsFieldJsValueWrapper(option._2)): _*)
  }

  def findCeasedRelationships(arn: String, service: String, clientId: String)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    find(Seq(
      "arn" -> arn,
      "service" -> service,
      "clientId" -> clientId,
      "fromCesa" -> "true") // FIXME change to status check
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2)): _*)
  }

  def createRelationship(relationship: Relationship)(implicit ec: ExecutionContext): Future[Unit] = {
      insert(relationship).map(_ => ())
  }

  def deleteRelationship(arn: String, service: String, clientId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      remove(
        "arn" -> arn,
        "service" -> service,
        "clientId" -> clientId)
        .map(result => if (result.n == 0) false else result.ok)
  }

  def findClientRelationships(service: String, clientId: String)(implicit ec: ExecutionContext): Future[List[Relationship]] = {
    find(Seq(
      "service" -> service,
      "clientId" -> clientId)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2)): _*)
  }

  def deleteRelationships(service: String, clientId: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    remove(
      "service" -> service,
      "clientId" -> clientId)
      .map(result => if (result.n == 0) false else result.ok)
  }
}
