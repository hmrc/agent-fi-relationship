/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.mvc.Request
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentfirelationship.connectors.{DesConnector, MappingConnector}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CesaRelationshipCopyService @Inject()(des: DesConnector,
                                            mapping: MappingConnector,
                                            auditService: AuditService) {

  def lookupCesaForOldRelationship(arn: Arn, nino: Nino)
                                  (implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[Any], auditData: AuditData): Future[Set[SaAgentReference]] = {
    auditData.set("regimeId", nino)
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

  private def intersection[A](cesaIds: Seq[A])(mappingServiceCall: => Future[Seq[A]])(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Set[A]] = {
    val cesaIdSet = cesaIds.toSet

    if (cesaIdSet.isEmpty) {
      Logger.warn("The sa references in cesa are empty.")
      Future.successful(Set.empty)
    } else
      mappingServiceCall.map { mappingServiceIds =>
        val intersected = mappingServiceIds.toSet.intersect(cesaIdSet)
        Logger.info(s"The sa references in mapping store are $mappingServiceIds. The intersected value between mapping store and DES is $intersected")
        intersected
      }
  }

}
