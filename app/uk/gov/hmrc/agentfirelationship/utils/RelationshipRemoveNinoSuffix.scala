/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.utils

import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext

import play.api.Logging
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentfirelationship.services.MongoLockService

@Singleton
class RelationshipRemoveNinoSuffix @Inject() (
    repository: RelationshipMongoRepository,
    mongoLockService: MongoLockService
)(implicit ec: ExecutionContext, appConfig: AppConfig)
    extends Logging {

  if (appConfig.removeNinoSuffixEnabled) {

    mongoLockService.relationshipLock("RelationshipRemoveNinoSuffix") {

      logger.info("[RelationshipRemoveNinoSuffix] Bulk NINO suffix removal job started")

      repository
        .removeNinoSuffixBulk()
        .map { totalUpdated =>
          logger.info(
            s"[RelationshipRemoveNinoSuffix] Job completed. Total records updated: $totalUpdated"
          )
        }
        .recover {
          case ex =>
            logger.error(
              "[RelationshipRemoveNinoSuffix] Bulk NINO suffix removal job failed",
              ex
            )
        }
    }
  } else {
    logger.info("[RelationshipRemoveNinoSuffix] removeNinoSuffixEnabled is disabled")
  }
}
