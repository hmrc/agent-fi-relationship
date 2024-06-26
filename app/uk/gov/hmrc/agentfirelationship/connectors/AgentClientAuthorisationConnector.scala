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

import play.api.http.Status.NOT_FOUND
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.Json
import play.api.Logging
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.models.SetRelationshipEndedPayload
import uk.gov.hmrc.agentfirelationship.utils.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

@Singleton
class AgentClientAuthorisationConnector @Inject() (appConfig: AppConfig, http: HttpClientV2, val metrics: Metrics)(
    implicit val ec: ExecutionContext
) extends HttpAPIMonitor
    with Logging {

  val baseUrl = appConfig.acaBaseUrl

  def setRelationshipEnded(arn: Arn, clientId: String, endedBy: String)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext
  ): Future[Boolean] =
    monitor("ConsumedAPI-SetRelationshipEnded-PUT") {
      val url         = new URL(s"$baseUrl/agent-client-authorisation/invitations/set-relationship-ended")
      val requestBody = SetRelationshipEndedPayload(arn, clientId, "PERSONAL-INCOME-RECORD", Some(endedBy))
      http.put(url).withBody(Json.toJson(requestBody)).execute[HttpResponse].map { response =>
        response.status match {
          case NO_CONTENT => true
          case NOT_FOUND =>
            logger.warn(s"no match found for set relationship ended")
            false
          case other => throw UpstreamErrorResponse("set-relationship-ended failed, status: ", other)
        }
      }
    }

}
