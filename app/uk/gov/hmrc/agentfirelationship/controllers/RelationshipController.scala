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
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentfirelationship.connectors.GovernmentGatewayProxyConnector
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future

@Singleton
class RelationshipController @Inject()(gg: GovernmentGatewayProxyConnector,
                                       auditService: AuditService,
                                       mongoService: RelationshipMongoService) extends BaseController {

  def findRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    mongoService.findRelationships(Relationship(Arn(arn), service, clientId)) map { result =>
      if (result.nonEmpty) Ok(toJson(result)) else {
        Logger.info("Unable to find a relationship")
        NotFound
      }
    }
  }

  def createRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    val maximumRelationshipCount = 2
    val relationship: Relationship = Relationship(Arn(arn), service, clientId)

    (for {
      relationshipList <- mongoService.findAllRelationshipsForAgent(arn)
      existingRelationship <- mongoService.findRelationships(relationship)
    } yield (relationshipList.length, existingRelationship.nonEmpty)) flatMap {
      case (size: Int, _) if size >= maximumRelationshipCount =>
        Logger.info("Maximum number of relationships reached")
        Future successful Forbidden
      case (_, true) =>
        Logger.info("Relationship already exists")
        Future successful Created
      case _ =>
        Logger.info("Creating a relationship")
        for {
          _ <- mongoService.createRelationship(relationship)
          _ = auditService.sendCreateRelationshipEvent(setAuditData(arn, service, clientId))
        } yield Created
    }
  }

  def deleteRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    Logger.info("Deleting a relationship")
    val relationshipDeleted: Future[Boolean] = for {
      successOrFail <- mongoService.deleteRelationship(Relationship(Arn(arn), service, clientId))
      _ = auditService.sendDeleteRelationshipEvent(setAuditData(arn, service, clientId))
    } yield successOrFail
    relationshipDeleted.map( {if (_) Ok else NotFound})
  }

  def payeCheckRelationship(arn: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    mongoService.findRelationships(Relationship(Arn(arn), "PAYE", clientId)) map { result =>
      if (result.nonEmpty) Ok else {
        Logger.info("No PAYE Relationship found")
        NotFound
      }
    }
  }

  private def setAuditData(arn: String, service: String, clientId: String)(implicit hc: HeaderCarrier): Future[AuditData] = {
    gg.getCredIdFor(Arn(arn)).map { credentialIdentifier ⇒
      val auditData = new AuditData()
      auditData.set("nino", clientId)
      auditData.set("regime", service)
      auditData.set("arn", arn)
      auditData.set("credId", credentialIdentifier)
      auditData
    }
  }
}
