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

package uk.gov.hmrc.agentfirelationship.connectors

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.agentfirelationship.config.MicroserviceAuthConnector
import uk.gov.hmrc.agentfirelationship.models.Auth._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton
class AgentClientAuthConnector @Inject() extends AuthorisedFunctions {
  implicit def hc(implicit rh: RequestHeader) = fromHeadersAndSession(rh.headers)

  override def authConnector: AuthConnector = MicroserviceAuthConnector

  private type AfiAction = Request[AnyContent] => TaxIdentifier => Future[Result]

  def authorisedForAfi(action: AfiAction): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(AuthProviders(GovernmentGateway)).retrieve(affinityGroupAllEnrolls) {
        case Some(AffinityGroup.Agent) ~ allEnrolments =>
          extractArn(allEnrolments.enrolments).fold(Future.successful(Forbidden(""))) { arn =>
            action(request)(arn)
          }
        case Some(_) ~ allEnrolments =>
          extractNino(allEnrolments.enrolments).fold(Future.successful(Forbidden(""))) { nino =>
            action(request)(nino)
          }
        case _ =>
          Logger.warn("Invalid affinity group or enrolments whilst trying to manipulate relationships")
          Future.successful(Forbidden)
      }.recoverWith {
        case ex: NoActiveSession =>
          Logger.warn("NoActiveSession exception whilst trying to manipulate relationships", ex)
          Future.successful(Unauthorized)
        case ex: AuthorisationException =>
          Logger.warn("Authorisation exception whilst trying to manipulate relationships", ex)
          Future.successful(Forbidden)
      }
  }

  private def extractArn(enrolls: Set[Enrolment]): Option[Arn] =
    enrolls.find(_.key equals "HMRC-AS-AGENT")
      .flatMap(_.getIdentifier("AgentReferenceNumber").map(x => Arn(x.value)))

  private def extractNino(enrolls: Set[Enrolment]): Option[Nino] = {
    enrolls.find(_.key == "HMRC-NI").flatMap(_.getIdentifier("NINO")).map(x => Nino(x.value))
  }
}
