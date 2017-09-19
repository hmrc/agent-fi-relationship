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

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentfirelationship.connectors.AuthConnector
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class RelationshipController @Inject()(authConnector: AuthConnector,
                                       auditService: AuditService,
                                       mongoService: RelationshipMongoService) extends BaseController {

  def findRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    mongoService.findRelationships(arn, service, clientId) map { result =>
      if (result.nonEmpty) Ok(toJson(result)) else NotFound
    }
  }

  def createRelationship(arn: String, service: String, clientId: String, startDate:String): Action[AnyContent] = Action.async { implicit request =>
    val relationship: Relationship = Relationship(Arn(arn), service, clientId, LocalDateTime.parse(startDate))
    mongoService.findRelationships(arn,service,clientId) flatMap {
      case Nil =>
        Logger.info("Creating a relationship")
        for {
          _ <- mongoService.createRelationship(relationship)
          auditData <- setAuditData(arn, clientId)
          _ <- auditService.sendCreateRelationshipEvent(auditData)
        } yield Created
      case _ =>
        Logger.info("Relationship already exists")
        Future successful Created
    }
  }

  def deleteRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    val relationshipDeleted: Future[Boolean] = for {
      successOrFail <- mongoService.deleteRelationship(arn, service, clientId)
      auditData <- setAuditData(arn, clientId)
      _ <- auditService.sendDeleteRelationshipEvent(auditData)
    } yield successOrFail
    relationshipDeleted.map(if (_) Ok else NotFound)
  }

  def afiCheckRelationship(arn: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    mongoService.findRelationships(arn, "afi", clientId) map { result =>
      if (result.nonEmpty) Ok else NotFound
    }
  }

  private def setAuditData(arn: String, clientId: String)(implicit hc: HeaderCarrier): Future[AuditData] = {
    authConnector.userDetails.map { userDetails =>
      val auditData = new AuditData()
      auditData.set("authProviderId", userDetails.authProviderId)
      auditData.set("arn", arn)
      auditData.set("regime", "afi")
      auditData.set("regimeId", clientId)
      auditData
    }
  }
}
