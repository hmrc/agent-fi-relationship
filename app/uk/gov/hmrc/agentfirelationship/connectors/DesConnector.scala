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

import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.utils.HttpAPIMonitor
import uk.gov.hmrc.agentfirelationship.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.Authorization
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

case class ClientRelationship(agents: Seq[Agent])

case class Agent(hasAgent: Boolean, agentId: Option[SaAgentReference], agentCeasedDate: Option[String])

object ClientRelationship {
  implicit val agentReads: Reads[Agent] = Json.reads[Agent]

  implicit val readClientRelationship: Reads[ClientRelationship] =
    (JsPath \ "agents")
      .readNullable[Seq[Agent]]
      .map(optionalAgents => ClientRelationship(optionalAgents.getOrElse(Seq.empty)))
}

@Singleton
class DesConnector @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(
    implicit val ec: ExecutionContext
) extends HttpAPIMonitor {

  private val Environment: String     = "Environment"
  private val CorrelationId: String   = "CorrelationId"
  private val Authorization_ : String = "Authorization"

  private def explicitHeaders: Seq[(String, String)] =
    Seq(
      Environment    -> s"${appConfig.desEnvironment}",
      CorrelationId  -> UUID.randomUUID().toString,
      Authorization_ -> s"Bearer ${appConfig.desAuthToken}"
    )

  def getClientSaAgentSaReferences(
      saTaxIdentifier: TaxIdentifier
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[SaAgentReference]] = {
    val url = {
      saTaxIdentifier match {
        case Nino(nino) => new URL(appConfig.desBaseUrl, s"/registration/relationship/nino/${encodePathSegment(nino)}")
        case Utr(utr)   => new URL(appConfig.desBaseUrl, s"/registration/relationship/utr/${encodePathSegment(utr)}")
        case _          => throw new RuntimeException("Unexpected TaxIdentifier")
      }
    }
    getWithDesHeaders[HttpResponse]("GetStatusAgentRelationship", url).map { response =>
      response.status match {
        case s if is2xx(s) =>
          response.json
            .as[ClientRelationship]
            .agents
            .filter(agent => agent.hasAgent && agent.agentCeasedDate.isEmpty)
            .flatMap(_.agentId)
        case NOT_FOUND =>
          Seq.empty // APB-4424: If the NINO is not even in CESA, then there can't be a relationship there
        case s =>
          throw UpstreamErrorResponse(s"Error calling: ${url.toString}", s)
      }
    }
  }

  private def getWithDesHeaders[A: HttpReads](
      apiName: String,
      url: URL
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] = {
    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer ${appConfig.desAuthToken}")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> appConfig.desEnvironment
    )
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      http
        .get(url)(desHeaderCarrier)
        .transform(_.addHttpHeaders(explicitHeaders: _*))
        .execute(implicitly[HttpReads[A]], ec)
    }
  }
}
