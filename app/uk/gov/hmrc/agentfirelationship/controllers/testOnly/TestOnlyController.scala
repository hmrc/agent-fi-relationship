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

package uk.gov.hmrc.agentfirelationship.controllers.testOnly

import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.api.Logging
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

@Singleton
class TestOnlyController @Inject() (mongoService: RelationshipMongoRepository, cc: ControllerComponents)(
    implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  case class Invitation(startDate: LocalDateTime)

  implicit val invitationFormat: OFormat[Invitation] = Json.format[Invitation]

  def createRelationship(arn: String, service: String, clientId: String): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      withJsonBody[Invitation] { invitation =>
        mongoService.findRelationships(arn, service, clientId, RelationshipStatus.Active).flatMap {
          case Nil =>
            logger.info("Creating a relationship")
            for {
              _ <- mongoService.createRelationship(
                Relationship(Arn(arn), service, clientId, Some(RelationshipStatus.Active), invitation.startDate, None)
              )
            } yield Created
          case _ =>
            logger.info("Relationship already exists")
            Future.successful(Created)
        }
      }

    }

  def terminateRelationship(arn: String, service: String, clientId: String): Action[AnyContent] =
    Action.async {
      val relationshipDeleted: Future[Boolean] = for {
        successOrFail <- mongoService.terminateRelationship(arn, service, clientId)
      } yield successOrFail
      relationshipDeleted.map(
        if (_) Ok
        else {
          logger.warn("Relationship Not Found")
          NotFound
        }
      )
    }

}
