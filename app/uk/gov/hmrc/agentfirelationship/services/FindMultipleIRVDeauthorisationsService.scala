/*
 * Copyright 2024 HM Revenue & Customs
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

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext

import play.api.Logging
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.mongo.lock.LockService
import uk.gov.hmrc.mongo.lock.MongoLockRepository

@Singleton
class FindMultipleIRVDeauthorisationsService @Inject() (
    relationshipMongoRepository: RelationshipMongoRepository,
    mongoLockRepository: MongoLockRepository,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  private val LOCK_ID = "startup-lock"

  private val enabled = appConfig.startupMongoQueryEnabled

  if (enabled) {
    val lockService: LockService = LockService(mongoLockRepository, lockId = LOCK_ID, ttl = 1.minute)
    logger.warn(s"Attempting to acquire lock....")
    lockService
      .withLock {
        logger.warn("Lock acquired. Starting query....")
        for {
          queryResult <- relationshipMongoRepository.findMultipleDeauthorisationsForIRV()
          _ = logger.warn(s"Found: $queryResult irv users with duplicate deauthorisations:")
        } yield ()
      }

  } else logger.warn("Startup mongo query not enabled.")

}
