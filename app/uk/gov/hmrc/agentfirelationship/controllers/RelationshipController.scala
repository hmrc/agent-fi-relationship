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

package uk.gov.hmrc.agentfirelationship.controllers

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class RelationshipController @Inject()(mongoService: RelationshipMongoService) extends BaseController {

  def findRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    mongoService.findRelationships(Relationship(Arn(arn), service, clientId)) map { result =>
      if (result.nonEmpty) Ok(toJson(result)) else {
        Logger.info("Unable to find a relationship")
        NotFound
      }
    }
  }

  def createRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    Logger.info("Creating a relationship")
    mongoService.createRelationship(Relationship(Arn(arn), service, clientId)).map(_ => Created)
  }

  def deleteRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    Logger.info("Deleting a relationship")
    mongoService.deleteRelationship(Relationship(Arn(arn), service, clientId)).map(_ => Ok)
  }

  def payeCheckRelationship(arn: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    mongoService.findRelationships(Relationship(Arn(arn), "PAYE", clientId)) map { result =>
      if (result.nonEmpty) Ok else {
        Logger.info("No PAYE Relationship found")
        NotFound
      }
    }
  }
}
