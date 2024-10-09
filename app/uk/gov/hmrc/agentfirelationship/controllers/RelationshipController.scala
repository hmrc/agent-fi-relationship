/*
 * Copyright 2023 HM Revenue & Customs
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
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.libs.json.OFormat
import play.api.mvc._
import play.api.Logging
import uk.gov.hmrc.agentfirelationship.audit.AuditData
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthConnector
import uk.gov.hmrc.agentfirelationship.connectors.DesConnector
import uk.gov.hmrc.agentfirelationship.models.DeletionCount
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.models.TerminationResponse
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentfirelationship.services.AgentClientAuthorisationService
import uk.gov.hmrc.agentfirelationship.services.CesaRelationshipCopyService
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class RelationshipController @Inject() (
    auditService: AuditService,
    mongoService: RelationshipMongoRepository,
    authConnector: AgentClientAuthConnector,
    checkCesaService: CesaRelationshipCopyService,
    acaService: AgentClientAuthorisationService,
    des: DesConnector,
    appConfig: AppConfig,
    cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  import appConfig.newStrideRole
  import appConfig.oldStrideRole

  val strideRoles: Seq[String] = Seq(oldStrideRole, newStrideRole)

  def findAfiRelationship(arn: String, clientId: String): Action[AnyContent] =
    findRelationship(arn, "PERSONAL-INCOME-RECORD", clientId)

  /* Tries to find existing client/agent relationships, and if none found, copies over from cesa */
  def findRelationship(arn: String, service: String, clientId: String): Action[AnyContent] = Action.async {
    implicit request =>
      implicit val auditData: AuditData = new AuditData()

      mongoService.findRelationships(arn, service, clientId, RelationshipStatus.Active).flatMap { activeRelationships =>
        if (activeRelationships.nonEmpty) {
          Future.successful(Ok(toJson(activeRelationships)))
        } else {
          mongoService.findAnyRelationships(arn, service, clientId).flatMap { anyRelationships =>
            if (anyRelationships.nonEmpty) {
              Future.successful(NotFound)
            } else {
              handleCesaRelationship(arn, service, clientId, activeRelationships)
            }
          }
        }
      }
  }

  private def handleCesaRelationship(
      arn: String,
      service: String,
      clientId: String,
      activeRelationships: List[Relationship]
  )(implicit auditData: AuditData, hc: HeaderCarrier, request: Request[Any]): Future[Result] =
    if (appConfig.checkCesaRelationshipFlag) {
      checkCesaService
        .lookupCesaForOldRelationship(Arn(arn), Nino(clientId))
        .flatMap { saAgentRefs =>
          if (saAgentRefs.isEmpty) {
            Future.successful(NotFound)
          } else {
            mongoService.findActiveClientRelationships(clientId).flatMap { activeRelationshipsAcrossAgents =>
              if (activeRelationshipsAcrossAgents.isEmpty) {
                if (appConfig.copyCesaRelationshipFlag) {
                  copyCesaRelationship(arn, service, clientId, activeRelationships)
                } else {
                  Future.successful(Ok(toJson(activeRelationships)))
                }
              } else {
                Future.successful(NotFound)
              }
            }

          }
        }
    } else {
      Future.successful(NotFound)
    }

  private def copyCesaRelationship(
      arn: String,
      service: String,
      clientId: String,
      activeRelationships: List[Relationship]
  )(implicit auditData: AuditData, hc: HeaderCarrier, request: Request[Any]): Future[Result] = {

    val activeRelationship = Relationship(
      arn = Arn(arn),
      service = service,
      clientId = clientId,
      relationshipStatus = Some(RelationshipStatus.Active),
      startDate = LocalDateTime.now(ZoneId.of("UTC")),
      endDate = None,
      fromCesa = Some(true)
    )

    (for {
      _         <- mongoService.createRelationship(activeRelationship)
      newResult <- mongoService.findRelationships(arn, service, clientId, RelationshipStatus.Active)
    } yield {
      auditData.set("agentReferenceNumber", arn)
      auditData.set("service", "personal-income-record")
      auditService.sendCreateRelationshipFromExisting(auditData)
      Ok(toJson(newResult))
    }).recover {
      case ex =>
        logger.error("Relationship creation failed", ex)
        Ok(toJson(activeRelationships))
    }
  }

  case class Invitation(startDate: LocalDateTime)
  implicit val invitationFormat: OFormat[Invitation] = Json.format[Invitation]

  def createRelationship(arn: String, service: String, clientId: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      authConnector.authorisedForAfi(strideRoles) { implicit taxIdentifier => implicit credentials =>
        withJsonBody[Invitation] { invitation =>
          forThisUser(Arn(arn), Nino(clientId), strideRoles) {
            mongoService.findRelationships(arn, service, clientId, RelationshipStatus.Active).flatMap {
              case Nil =>
                logger.info("Creating a relationship")
                for {
                  similar <- mongoService.findClientRelationships(service, clientId, RelationshipStatus.Active)
                  _ <- Future.sequence(
                    similar.map(r =>
                      mongoService
                        .terminateRelationship(r.arn.value, service, clientId)
                        .map(terminated => {
                          if (terminated) {
                            logger.info(s"Terminated Relationship true")
                            acaService
                              .setRelationshipEnded(r.arn, clientId)
                              .map(ended =>
                                if (ended) logger.info("set relationship ended for other invitation succeeded")
                                else logger.warn("failed to set relationship ended for other invitation")
                              )
                          } else
                            logger.warn(
                              "failed to end other relationship...leaving the status of the other invitation unchanged."
                            )
                        })
                    )
                  )
                  _ <- mongoService.createRelationship(
                    Relationship(
                      Arn(arn),
                      service,
                      clientId,
                      Some(RelationshipStatus.Active),
                      invitation.startDate,
                      None
                    )
                  )
                  auditData <- setAuditData(arn, clientId, credentials)
                  _         <- auditService.sendCreateRelationshipEvent(auditData)
                } yield Created
              case _ =>
                logger.info("Relationship already exists")
                Future.successful(Created)
            }
          }
        }
      }
    }

  def terminateRelationship(arn: String, service: String, clientId: String): Action[AnyContent] =
    Action.async { implicit request =>
      authConnector.authorisedForAfi(strideRoles) { implicit taxIdentifier => implicit credentials =>
        forThisUser(Arn(arn), Nino(clientId), strideRoles) {
          val relationshipDeleted: Future[Boolean] = for {
            successOrFail <- mongoService.terminateRelationship(arn, service, clientId)
            auditData     <- setAuditData(arn, clientId, credentials)
            _             <- sendAuditEventForThisUser(credentials, auditData)
          } yield successOrFail
          relationshipDeleted.map(if (_) {
            acaService.setRelationshipEnded(Arn(arn), clientId)
            Ok
          } else {
            logger.warn("Relationship Not Found")
            NotFound
          })
        }
      }
    }

  def findClientRelationships(service: String, clientId: String): Action[AnyContent] =
    Action.async {
      mongoService.findClientRelationships(service, clientId, RelationshipStatus.Active).map { result =>
        if (result.nonEmpty) Ok(toJson(result)) else NotFound
      }
    }

  val findInactiveRelationships: Action[AnyContent] = Action.async { implicit request =>
    authConnector.authorisedForAfi(strideRoles) { implicit taxIdentifier => credentials =>
      taxIdentifier match {
        case Some(Arn(arn)) if Arn.isValid(arn) =>
          mongoService.findInactiveAgentRelationships(arn).map { result =>
            if (result.nonEmpty) Ok(toJson(result))
            else {
              logger.warn("No Inactive Relationships Found For ARN")
              NotFound
            }
          }
        case Some(Nino(nino)) if Nino.isValid(nino) =>
          mongoService.findInactiveClientRelationships(nino).map { result =>
            if (result.nonEmpty) Ok(toJson(result))
            else {
              logger.warn("No Inactive Relationships Found For NINO")
              NotFound
            }
          }
        case _ =>
          logger.error("Arn Not Found in Login")
          Future.successful(NotFound)
      }
    }
  }

  val findActiveRelationships: Action[AnyContent] = Action.async { implicit request =>
    authConnector.authorisedForAfi(strideRoles) { implicit taxIdentifier => credentials =>
      taxIdentifier match {
        case Some(Arn(arn)) if Arn.isValid(arn) =>
          mongoService.findActiveAgentRelationships(arn).map { result =>
            if (result.nonEmpty) Ok(toJson(result))
            else {
              logger.warn("No Active Relationships Found For ARN")
              NotFound
            }
          }
        case Some(Nino(nino)) if Nino.isValid(nino) =>
          mongoService.findActiveClientRelationships(nino).map { result =>
            if (result.nonEmpty) Ok(toJson(result))
            else {
              logger.warn("No Active Relationships Found For NINO")
              NotFound
            }
          }
        case _ =>
          logger.error("Arn/Nino Not Found in Login")
          Future.successful(NotFound)
      }
    }
  }

  def hasLegacySaRelationship(utr: Utr): Action[AnyContent] = Action.async { implicit request =>
    des.getClientSaAgentSaReferences(utr).map {
      case Nil => NotFound
      case _   => Ok
    }
  }

  def removeAFIRelationshipsForAgent(arn: String): Action[AnyContent] = Action.async { implicit request =>
    authConnector.withBasicAuth(appConfig.expectedAuth) {
      if (Arn.isValid(arn)) {
        mongoService
          .terminateAgentRelationship(arn)
          .map { result =>
            Ok(
              Json.toJson[TerminationResponse](
                TerminationResponse(result.map(count => DeletionCount(appConfig.appName, "fi-relationship", count)))
              )
            )
          }
          .recover {
            case e =>
              logger.warn(s"Something has gone for $arn due to: ${e.getMessage}")
              InternalServerError
          }
      } else Future.successful(BadRequest)
    }
  }

  private def setAuditData(arn: String, clientId: String, creds: Credentials): Future[AuditData] = {
    val auditData = new AuditData()
    if (creds.providerType == "GovernmentGateway") {
      auditData.set("authProviderId", creds.providerId)
      auditData.set("authProviderIdType", creds.providerType)
      auditData.set("agentReferenceNumber", arn)
      auditData.set("service", "personal-income-record")
      auditData.set("clientId", clientId)
      auditData.set("clientIdType", "ni")
      Future.successful(auditData)
    } else if (creds.providerType == "PrivilegedApplication") {
      auditData.set("authProviderId", creds.providerId)
      auditData.set("authProviderIdType", creds.providerType)
      auditData.set("agentReferenceNumber", arn)
      auditData.set("service", "personal-income-record")
      auditData.set("clientId", clientId)
      Future.successful(auditData)
    } else
      throw new IllegalArgumentException("No providerType found in Credentials")

  }

  private def sendAuditEventForThisUser(
      credentials: Credentials,
      auditData: AuditData
  )(implicit hc: HeaderCarrier, request: Request[Any]): Future[Unit] =
    if (credentials.providerType == "GovernmentGateway")
      auditService.sendTerminatedRelationshipEvent(auditData)
    else if (credentials.providerType == "PrivilegedApplication")
      auditService.sendHmrcLedDeleteRelationshipAuditEvent(auditData)
    else
      throw new IllegalArgumentException("No providerType found in Credentials")

  private def forThisUser(requestedArn: Arn, requestedNino: Nino, strideRoles: Seq[String])(
      action: => Future[Result]
  )(implicit taxIdentifier: Option[TaxIdentifier]) =
    taxIdentifier match {
      case Some(t) =>
        t match {
          case arn @ Arn(_) if isDifferentIdentifier(requestedArn, arn) => {
            logger.warn("Arn does not match")
            Future.successful(Forbidden)
          }
          case nino @ Nino(_) if isDifferentIdentifier(requestedNino, nino) => {
            logger.warn("Nino does not match")
            Future.successful(Forbidden)
          }
          case _ => {
            action
          }
        }
      case _ =>
        strideRoles match {
          case roles if roles.contains(appConfig.oldStrideRole) || roles.contains(appConfig.newStrideRole) => action
          case _ =>
            logger.warn("Unsupported ProviderType / Role")
            Future.successful(Forbidden)
        }
    }

  private def isDifferentIdentifier(taxIdentifier1: TaxIdentifier, taxIdentifier2: TaxIdentifier): Boolean =
    taxIdentifier1.getClass != taxIdentifier2.getClass || normalized(taxIdentifier1.value) != normalized(
      taxIdentifier2.value
    )

  private def normalized(value: String) = value.toLowerCase.replace(" ", "")
}
