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

package uk.gov.hmrc.agentfirelationship.controllers

import java.time.{LocalDateTime, ZoneId}
import javax.inject.{Inject, Named, Singleton}

import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc._
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthConnector
import uk.gov.hmrc.agentfirelationship.models.{Relationship, RelationshipStatus}
import uk.gov.hmrc.agentfirelationship.services.{CesaRelationshipCopyService, RelationshipMongoService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class RelationshipController @Inject()(
  auditService: AuditService,
  mongoService: RelationshipMongoService,
  authConnector: AgentClientAuthConnector,
  checkCesaService: CesaRelationshipCopyService,
  @Named("features.check-cesa-relationships") checkCesaRelationships: Boolean,
  @Named("features.copy-cesa-relationships") copyCesaRelationships: Boolean,
  @Named("auth.stride.role") strideRole: String)
    extends BaseController {

  def findAfiRelationship(arn: String, clientId: String): Action[AnyContent] =
    findRelationship(arn, "PERSONAL-INCOME-RECORD", clientId)

  def findRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async {
    implicit request =>
      implicit val auditData = new AuditData()
      mongoService.findRelationships(arn, service, clientId, RelationshipStatus.Active) flatMap { result =>
        if (result.nonEmpty) {
          Future.successful(Ok(toJson(result)))
        } else {
          mongoService.findAnyRelationships(arn, service, clientId) flatMap { previousRelationships =>
            if (previousRelationships.nonEmpty) {
              Future successful NotFound
            } else {
              if (checkCesaRelationships) {
                checkCesaService.lookupCesaForOldRelationship(Arn(arn), Nino(clientId)).flatMap { saAgentRefs =>
                  if (saAgentRefs.isEmpty) {
                    Future successful NotFound
                  } else {
                    if (copyCesaRelationships) {
                      val activeRelationship = Relationship(
                        arn = Arn(arn),
                        service = service,
                        clientId = clientId,
                        relationshipStatus = Some(RelationshipStatus.Active),
                        startDate = LocalDateTime.now(ZoneId.of("UTC")),
                        endDate = None,
                        fromCesa = Some(true)
                      )
                      mongoService
                        .createRelationship(activeRelationship)
                        .flatMap(_ => mongoService.findRelationships(arn, service, clientId, RelationshipStatus.Active))
                        .map(newResult => {
                          auditData.set("agentReferenceNumber", arn)
                          auditData.set("service", "personal-income-record")
                          auditService.sendCreateRelationshipFromExisting(auditData)
                          Ok(toJson(newResult))
                        })
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

  case class Invitation(startDate: LocalDateTime)
  implicit val invitationFormat = Json.format[Invitation]

  def createRelationship(arn: String, service: String, clientId: String) = Action.async(parse.json) {
    implicit request =>
      authConnector.authorisedForAfi(strideRole) { implicit taxIdentifier => implicit credentials =>
        withJsonBody[Invitation] { invitation =>
          forThisUser(Arn(arn), Nino(clientId), strideRole) {
            mongoService.findRelationships(arn, service, clientId, RelationshipStatus.Active) flatMap {
              case Nil =>
                Logger.info("Creating a relationship")
                for {
                  _ <- mongoService.createRelationship(
                        Relationship(
                          Arn(arn),
                          service,
                          clientId,
                          Some(RelationshipStatus.Active),
                          invitation.startDate,
                          None))
                  auditData <- setAuditData(arn, clientId, credentials)
                  _         <- auditService.sendCreateRelationshipEvent(auditData)
                } yield Created
              case _ =>
                Logger.info("Relationship already exists")
                Future successful Created
            }
          }
        }
      }
  }

  def terminateRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async {
    implicit request =>
      authConnector.authorisedForAfi(strideRole) { implicit taxIdentifier => implicit credentials =>
        forThisUser(Arn(arn), Nino(clientId), strideRole) {
          val relationshipDeleted: Future[Boolean] = for {
            successOrFail <- mongoService.terminateRelationship(arn, service, clientId)
            auditData     <- setAuditData(arn, clientId, credentials)
            _             <- sendAuditEventForThisUser(credentials, auditData)
          } yield successOrFail
          relationshipDeleted.map(
            if (_) Ok
            else {
              Logger.warn("Relationship Not Found")
              NotFound
            })

        }
      }
  }

  def findClientRelationships(service: String, clientId: String): Action[AnyContent] = Action.async {
    implicit request =>
      mongoService.findClientRelationships(service, clientId, RelationshipStatus.Active) map { result =>
        if (result.nonEmpty) Ok(toJson(result)) else NotFound
      }
  }

  val findInActiveRelationshipsForAgent: Action[AnyContent] = Action.async { implicit request =>
    authConnector.authorisedForAfi(strideRole) { implicit taxIdentifier => implicit credentials =>
      taxIdentifier match {
        case Some(Arn(arn)) if Arn.isValid(arn) =>
          mongoService.findInActiveAgentRelationships(arn).map { result =>
            if (result.nonEmpty) Ok(toJson(result))
            else {
              Logger(getClass).warn("No Inactive Relationships Found")
              NotFound
            }
          }
        case _ =>
          Logger(getClass).error("Arn Not Found in Login")
          Future successful NotFound
      }
    }

  }

  private def setAuditData(arn: String, clientId: String, creds: Credentials)(
    implicit hc: HeaderCarrier): Future[AuditData] = {
    val auditData = new AuditData()
    if (creds.providerType == "GovernmentGateway") {
      auditData.set("authProviderId", creds.providerId)
      auditData.set("authProviderIdType", creds.providerType)
      auditData.set("agentReferenceNumber", arn)
      auditData.set("service", "personal-income-record")
      auditData.set("clientId", clientId)
      auditData.set("clientIdType", "ni")
      Future successful auditData
    } else if (creds.providerType == "PrivilegedApplication") {
      auditData.set("authProviderId", creds.providerId)
      auditData.set("authProviderIdType", creds.providerType)
      auditData.set("agentReferenceNumber", arn)
      auditData.set("service", "personal-income-record")
      auditData.set("clientId", clientId)
      Future successful auditData
    } else throw new IllegalArgumentException("No providerType found in Credentials")

  }

  private def sendAuditEventForThisUser(credentials: Credentials, auditData: AuditData)(
    implicit hc: HeaderCarrier,
    request: Request[Any]): Future[Unit] =
    if (credentials.providerType == "GovernmentGateway")
      auditService.sendTerminatedRelationshipEvent(auditData)
    else if (credentials.providerType == "PrivilegedApplication")
      auditService.sendHmrcLedDeleteRelationshipAuditEvent(auditData)
    else throw new IllegalArgumentException("No providerType found in Credentials")

  private def forThisUser(requestedArn: Arn, requestedNino: Nino, strideRole: String)(action: => Future[Result])(
    implicit taxIdentifier: Option[TaxIdentifier]) =
    taxIdentifier match {
      case Some(t) =>
        t match {
          case arn @ Arn(_) if isDifferentIdentifier(requestedArn, arn) => {
            Logger.warn("Arn does not match")
            Future.successful(Forbidden)
          }
          case nino @ Nino(_) if isDifferentIdentifier(requestedNino, nino) => {
            Logger.warn("Nino does not match")
            Future.successful(Forbidden)
          }
          case _ => {
            action
          }
        }
      case _ =>
        strideRole match {
          case "Maintain Agent client relationships" => action
          case _ =>
            Logger.warn("Unsupported ProviderType / Role")
            Future successful Forbidden
        }
    }

  private def isDifferentIdentifier(taxIdentifier1: TaxIdentifier, taxIdentifier2: TaxIdentifier): Boolean =
    taxIdentifier1.getClass != taxIdentifier2.getClass || normalized(taxIdentifier1.value) != normalized(
      taxIdentifier2.value)

  private def normalized(value: String) = value.toLowerCase.replace(" ", "")
}
