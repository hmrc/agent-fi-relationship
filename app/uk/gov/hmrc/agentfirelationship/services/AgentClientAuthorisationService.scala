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

package uk.gov.hmrc.agentfirelationship.services

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthorisationConnector
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class AgentClientAuthorisationService @Inject() (acaConnector: AgentClientAuthorisationConnector) {

  def setRelationshipEnded(
      arn: Arn,
      clientId: String
  )(implicit taxIdentifier: Option[TaxIdentifier], hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val endedBy = taxIdentifier match {
      case Some(Arn(_))  => "Agent"
      case Some(Nino(_)) => "Client"
      case _             => "HMRC"
    }
    acaConnector.setRelationshipEnded(arn, clientId, endedBy)
  }
}
