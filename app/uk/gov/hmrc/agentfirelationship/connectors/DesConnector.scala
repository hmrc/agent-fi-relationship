/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

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

  def getClientSaAgentSaReferences(
    nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[SaAgentReference]] = {
    val url = new URL(appConfig.desBaseUrl, s"/registration/relationship/nino/${encodePathSegment(nino.value)}")
    getWithDesHeaders[ClientRelationship]("GetStatusAgentRelationship", url)
      .map(
        _.agents
          .filter(agent => agent.hasAgent && agent.agentCeasedDate.isEmpty)
          .flatMap(_.agentId))
      .recover {

        // APB-4424: If the NINO is not even in CESA, then there can't be a relationship there
        case _: uk.gov.hmrc.http.NotFoundException => Seq.empty
      }
  }

  private def getWithDesHeaders[A: HttpReads](apiName: String, url: URL)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[A] = {
    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer ${appConfig.desAuthToken}")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> appConfig.desEnvironment)
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      http.GET[A](url.toString)(implicitly[HttpReads[A]], desHeaderCarrier, ec)
    }
  }

}
