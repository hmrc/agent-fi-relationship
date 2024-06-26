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

package uk.gov.hmrc.agentfirelationship.connectors

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.matching.Regex

import play.api.mvc._
import play.api.mvc.Results._
import play.api.Logging
import uk.gov.hmrc.agentfirelationship.models.Auth._
import uk.gov.hmrc.agentfirelationship.models.BasicAuthentication
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.play.http.HeaderCarrierConverter

@Singleton
class AgentClientAuthConnector @Inject() (val authConnector: AuthConnector)(implicit ec: ExecutionContext)
    extends AuthorisedFunctions
    with Logging {
  implicit def hc(implicit rh: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(rh, rh.session)

  private type AfiAction =
    Option[TaxIdentifier] => Credentials => Future[Result]

  def authorisedForAfi(strideRoles: Seq[String])(action: AfiAction)(implicit hc: HeaderCarrier): Future[Result] =
    authorised()
      .retrieve(affinityGroupAllEnrollsCreds) {
        case affinity ~ enrols ~ optCreds =>
          (affinity, optCreds) match {
            case (Some(AffinityGroup.Agent), Some(creds @ Credentials(_, "GovernmentGateway"))) =>
              extractArn(enrols.enrolments).fold(Future.successful(Forbidden(""))) { arn =>
                action(Some(arn))(creds)
              }
            case (Some(_), Some(creds @ Credentials(_, "GovernmentGateway"))) =>
              extractNino(enrols.enrolments).fold(Future.successful(Forbidden(""))) { nino =>
                action(Some(nino))(creds)
              }
            case (_, Some(creds @ Credentials(_, "PrivilegedApplication")))
                if hasRequiredStrideRole(enrols, strideRoles) =>
              action(None)(creds)
            case _ =>
              Future.successful(Forbidden("Invalid affinity group and credentials found"))
          }
        case _ =>
          logger.warn("Invalid affinity group or enrolments or credentials whilst trying to manipulate relationships")
          Future.successful(Forbidden)
      }
      .recoverWith {
        case ex: NoActiveSession =>
          logger.warn("NoActiveSession exception whilst trying to manipulate relationships", ex)
          Future.successful(Unauthorized)
        case ex: AuthorisationException =>
          logger.warn("Authorisation exception whilst trying to manipulate relationships", ex)
          Future.successful(Forbidden)
      }

  val basicAuthHeader: Regex = "Basic (.+)".r
  val decodedAuth: Regex     = "(.+):(.+)".r

  private def decodeFromBase64(encodedString: String): String =
    try {
      new String(Base64.getDecoder.decode(encodedString), UTF_8)
    } catch { case _: Throwable => "" }

  def withBasicAuth(
      expectedAuth: BasicAuthentication
  )(body: => Future[Result])(implicit request: Request[_]): Future[Result] =
    request.headers.get(HeaderNames.authorisation) match {
      case Some(basicAuthHeader(encodedAuthHeader)) =>
        decodeFromBase64(encodedAuthHeader) match {
          case decodedAuth(username, password) =>
            if (BasicAuthentication(username, password) == expectedAuth) body
            else {
              logger.warn("Authorization header found in the request but invalid username or password")
              Future.successful(Unauthorized)
            }
          case _ =>
            logger.warn("Authorization header found in the request but its not in the expected format")
            Future.successful(Unauthorized)
        }
      case _ =>
        logger.warn("No Authorization header found in the request for agent termination")
        Future.successful(Unauthorized)
    }

  def onlyStride(
      strideRole: String
  )(action: => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(PrivilegedApplication))
      .retrieve(allEnrolments) {
        case allEnrols if allEnrols.enrolments.map(_.key).contains(strideRole) => action
        case e =>
          logger.warn(s"Unauthorized Discovered during Stride Authentication: ${e.enrolments.map(_.key)}")
          Future.successful(Unauthorized)
      }
      .recover {
        case e =>
          logger.warn(s"Error Discovered during Stride Authentication: ${e.getMessage}")
          Forbidden
      }

  case class CurrentUser(credentials: Credentials, affinityGroup: Option[AffinityGroup])

  def hasRequiredStrideRole(enrolments: Enrolments, strideRoles: Seq[String]): Boolean =
    strideRoles.exists(s => enrolments.enrolments.exists(_.key == s))

  private def extractArn(enrolls: Set[Enrolment]): Option[Arn] =
    enrolls
      .find(_.key.equals("HMRC-AS-AGENT"))
      .flatMap(_.getIdentifier("AgentReferenceNumber").map(x => Arn(x.value)))

  private def extractNino(enrolls: Set[Enrolment]): Option[Nino] =
    enrolls
      .find(_.key == "HMRC-NI")
      .flatMap(_.getIdentifier("NINO"))
      .map(x => Nino(x.value))

}
