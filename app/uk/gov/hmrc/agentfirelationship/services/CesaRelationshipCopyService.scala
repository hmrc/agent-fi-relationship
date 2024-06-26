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

import play.api.Logging
import uk.gov.hmrc.agentfirelationship.audit.AuditData
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.connectors.DesConnector
import uk.gov.hmrc.agentfirelationship.connectors.MappingConnector
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class CesaRelationshipCopyService @Inject() (des: DesConnector, mapping: MappingConnector, auditService: AuditService)
    extends Logging {

  def lookupCesaForOldRelationship(
      arn: Arn,
      nino: Nino
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, auditData: AuditData): Future[Set[SaAgentReference]] = {
    auditData.set("clientId", nino)
    for {
      references <- des.getClientSaAgentSaReferences(nino)
      matching <- intersection(references) {
        mapping.getSaAgentReferencesFor(arn)
      }
      _ = auditData.set("saAgentRef", matching.mkString(","))
    } yield {
      matching
    }
  }

  private def intersection[A](
      cesaIds: Seq[A]
  )(mappingServiceCall: => Future[Seq[A]])(implicit ec: ExecutionContext): Future[Set[A]] = {
    val cesaIdSet = cesaIds.toSet

    if (cesaIdSet.isEmpty) {
      logger.warn("The sa references in cesa are empty.")
      Future.successful(Set.empty)
    } else
      mappingServiceCall.map { mappingServiceIds =>
        val intersected = mappingServiceIds.toSet.intersect(cesaIdSet)
        logger.info(
          s"The sa references in mapping store are $mappingServiceIds. The intersected value between mapping store and DES is $intersected"
        )
        intersected
      }
  }

}
