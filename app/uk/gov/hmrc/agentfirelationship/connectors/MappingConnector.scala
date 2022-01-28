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
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

case class Mappings(mappings: Seq[Mapping])

case class Mapping(arn: Arn, saAgentReference: SaAgentReference)

object Mappings {
  implicit val mappingReads = Json.reads[Mapping]
  implicit val reads = Json.reads[Mappings]
}

@Singleton
class MappingConnector @Inject()(appConfig: AppConfig, httpGet: HttpClient, metrics: Metrics)(implicit ec: ExecutionContext) extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getSaAgentReferencesFor(arn: Arn)(implicit hc: HeaderCarrier): Future[Seq[SaAgentReference]] = {
    val url = new URL(appConfig.agentMappingBaseUrl, s"/agent-mapping/mappings/sa/${arn.value}")
    monitor(s"ConsumedAPI-Digital-Mappings-GET") {
      httpGet
        .GET[HttpResponse](url.toString)
        .map { response =>
          response.status match {
            case s if is2xx(s) => response.json.as[Mappings].mappings.map(_.saAgentReference)
            case NOT_FOUND     => Seq.empty
            case s             => throw UpstreamErrorResponse(s"Error calling: ${url.toString}", s)
          }
        }
    }
  }
}
