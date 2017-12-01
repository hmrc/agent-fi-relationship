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
import javax.inject.{Inject, Named, Singleton}

import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentfirelationship.connectors.{AgentClientAuthConnector, AuthAuditConnector}
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.services.{CesaRelationshipCopyService, RelationshipMongoService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class RelationshipController @Inject()(authAuditConnector: AuthAuditConnector,
                                       auditService: AuditService,
                                       mongoService: RelationshipMongoService,
                                       authConnector: AgentClientAuthConnector,
                                       checkCesaService: CesaRelationshipCopyService,
                                       @Named("features.check-cesa-relationships") checkCesaRelationships: Boolean,
                                       @Named("features.copy-cesa-relationships") copyCesaRelationships: Boolean)
  extends BaseController {

  private def searchCesaRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    implicit val auditData = new AuditData()
    mongoService.findRelationships(arn, service, clientId) flatMap { result =>
      if (result.nonEmpty) {
        Future.successful(Ok(toJson(result)))
      } else {
        mongoService.findCesaRelationships(arn, service, clientId) flatMap { cesaRelationship =>
          if (cesaRelationship.nonEmpty) {
            Future successful Ok(toJson(cesaRelationship))
          } else {
            if (checkCesaRelationships) {
              checkCesaService.lookupCesaForOldRelationship(Arn(arn), Nino(clientId)).flatMap { saAgentRefs =>
                if (saAgentRefs.isEmpty) {
                  Future successful NotFound
                } else {
                  if (copyCesaRelationships) {
                    mongoService.createRelationship(Relationship(Arn(arn), service, clientId, LocalDateTime.now(), fromCesa = true))
                      .flatMap(_ => mongoService.findRelationships(arn, service, clientId))
                      .map(newResult => Ok(toJson(newResult)))
                      .recover {
                        case ex =>
                          Logger.error("Relationship creation failed", ex)
                          Ok(toJson(result))
                      }
                  } else {
                    Future successful Ok(toJson(result))
                  }
                }
              }
            } else {
              Future.successful(NotFound)
            }
          }
        }
      }
    }
  }

  def findRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = searchCesaRelationship(arn, service, clientId)

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

  def afiCheckRelationship(arn: String, clientId: String): Action[AnyContent] = searchCesaRelationship(arn, "afi", clientId)

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
