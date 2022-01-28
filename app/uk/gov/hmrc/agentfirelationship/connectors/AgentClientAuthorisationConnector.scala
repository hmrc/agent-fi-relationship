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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import play.api.http.Status.{NOT_FOUND, NO_CONTENT}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.models.SetRelationshipEndedPayload
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentClientAuthorisationConnector @Inject()(appConfig: AppConfig, http: HttpClient, metrics: Metrics) extends HttpAPIMonitor with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  val baseUrl = appConfig.acaBaseUrl

  def setRelationshipEnded(arn: Arn, clientId: String, endedBy: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    monitor("ConsumedAPI-SetRelationshipEnded-PUT") {
      val url = s"$baseUrl/agent-client-authorisation/invitations/set-relationship-ended"
      val requestBody = SetRelationshipEndedPayload(arn, clientId, "PERSONAL-INCOME-RECORD", Some(endedBy))
      http.PUT[SetRelationshipEndedPayload, HttpResponse](url, requestBody).map { response =>
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
