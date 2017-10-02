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
import uk.gov.hmrc.agentfirelationship.connectors.{AgentClientAuthConnector, AuthAuditConnector}
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class RelationshipController @Inject()(authAuditConnector: AuthAuditConnector,
                                       auditService: AuditService,
                                       mongoService: RelationshipMongoService,
                                       authConnector: AgentClientAuthConnector) extends BaseController {

  def findRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    mongoService.findRelationships(arn, service, clientId) map { result =>
      if (result.nonEmpty) Ok(toJson(result)) else NotFound
    }
  }

  def createRelationship(arn: String, service: String, clientId: String, startDate: String): Action[AnyContent] =
    authConnector.authorisedForAfi { implicit request =>
      implicit taxIdentifier =>
        forThisUser(Arn(arn), Nino(clientId)) {
          mongoService.findRelationships(arn, service, clientId) flatMap {
            case Nil =>
              Logger.info("Creating a relationship")
              for {
                _ <- mongoService.createRelationship(Relationship(Arn(arn), service, clientId, LocalDateTime.parse(startDate)))
                auditData <- setAuditData(arn, clientId)
                _ <- auditService.sendCreateRelationshipEvent(auditData)
              } yield Created
            case _ =>
              Logger.info("Relationship already exists")
              Future successful Created
          }

        }
    }

  def deleteRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = authConnector.authorisedForAfi {
    implicit request =>
      implicit taxIdentifier =>
        forThisUser(Arn(arn), Nino(clientId)) {
          val relationshipDeleted: Future[Boolean] = for {
            successOrFail <- mongoService.deleteRelationship(arn, service, clientId)
            auditData <- setAuditData(arn, clientId)
            _ <- auditService.sendDeleteRelationshipEvent(auditData)
          } yield successOrFail
          relationshipDeleted.map(if (_) Ok else NotFound)

        }
  }

  def afiCheckRelationship(arn: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    mongoService.findRelationships(arn, "afi", clientId) map { result =>
      if (result.nonEmpty) Ok else NotFound
    }
  }

  private def setAuditData(arn: String, clientId: String)(implicit hc: HeaderCarrier): Future[AuditData] = {
    authAuditConnector.userDetails.map { userDetails =>
      val auditData = new AuditData()
      auditData.set("authProviderId", userDetails.authProviderId)
      auditData.set("arn", arn)
      auditData.set("regime", "afi")
      auditData.set("regimeId", clientId)
      auditData
    }
  }

  private def forThisUser(requestedArn: Arn, requestedNino: Nino)(block: => Future[Result])(implicit taxIdentifier: TaxIdentifier) = {
    taxIdentifier match {
      case arn@Arn(_) if requestedArn != arn => Future.successful(Forbidden)
      case nino@Nino(_) if requestedNino != nino => Future.successful(Forbidden)
      case _ => block
    }
  }
}
