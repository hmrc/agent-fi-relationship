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
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.models.Arn
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpErrorFunctions._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

case class Mappings(mappings: Seq[Mapping])

case class Mapping(arn: Arn, saAgentReference: SaAgentReference)

object Mappings {
  implicit val mappingReads: Reads[Mapping] = Json.reads[Mapping]
  implicit val reads: Reads[Mappings]       = Json.reads[Mappings]
}

@Singleton
class MappingConnector @Inject() (appConfig: AppConfig, httpGet: HttpClientV2, val metrics: Metrics)(
    implicit val ec: ExecutionContext
) {

  def getSaAgentReferencesFor(arn: Arn)(implicit hc: HeaderCarrier): Future[Seq[SaAgentReference]] = {
    val url = new URL(appConfig.agentMappingBaseUrl, s"/agent-mapping/mappings/sa/${arn.value}")
    httpGet
      .get(url)
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case s if is2xx(s) => response.json.as[Mappings].mappings.map(_.saAgentReference)
          case NOT_FOUND     => Seq.empty
          case s             => throw UpstreamErrorResponse(s"Error calling: ${url.toString}", s)
        }

      }
  }
}
