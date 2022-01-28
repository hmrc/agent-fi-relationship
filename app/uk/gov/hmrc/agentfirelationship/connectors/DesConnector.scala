/*
 * Copyright 2022 HM Revenue & Customs
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
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics

import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentfirelationship.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HttpClient, HttpReads, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpErrorFunctions._

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HttpReads.Implicits._
import play.api.http.Status._
import uk.gov.hmrc.agentmtdidentifiers.model.Utr

import java.util.UUID

case class ClientRelationship(agents: Seq[Agent])

case class Agent(hasAgent: Boolean, agentId: Option[SaAgentReference], agentCeasedDate: Option[String])

object ClientRelationship {
  implicit val agentReads = Json.reads[Agent]

  implicit val readClientRelationship =
    (JsPath \ "agents")
      .readNullable[Seq[Agent]]
      .map(optionalAgents => ClientRelationship(optionalAgents.getOrElse(Seq.empty)))
}

@Singleton
class DesConnector @Inject()(appConfig: AppConfig, http: HttpClient, metrics: Metrics) extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val Environment = "Environment"
  private val CorrelationId = "CorrelationId"
  private val Authorization_ = "Authorization"

  private def explicitHeaders =
    Seq(
      Environment    -> s"${appConfig.desEnvironment}",
      CorrelationId  -> UUID.randomUUID().toString,
      Authorization_ -> s"Bearer ${appConfig.desAuthToken}"
    )

  def getClientSaAgentSaReferences(
    saTaxIdentifier: TaxIdentifier)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[SaAgentReference]] = {
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

  private def getWithDesHeaders[A: HttpReads](apiName: String, url: URL)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] = {
    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer ${appConfig.desAuthToken}")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> appConfig.desEnvironment)
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      http.GET[A](url.toString, headers = explicitHeaders)(implicitly[HttpReads[A]], desHeaderCarrier, ec)
    }
  }
}
