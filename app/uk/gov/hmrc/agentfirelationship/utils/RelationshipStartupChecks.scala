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
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository

@Singleton
class RelationshipStartupChecks @Inject() (
    repository: RelationshipMongoRepository
)(implicit ec: ExecutionContext)
    extends Logging {

  for {
    allDuplicates      <- repository.getAllDuplicateNinoRecords
    duplicateSuffixes  <- repository.getDuplicateNinoWoSuffixRecords
    getLatestDuplicate <- repository.getLastCreatedDuplicateNinoRecord
  } yield logger.warn(
    s"""
       |[RelationshipStartupChecks] All duplicate ninos: $allDuplicates
       | ---- Duplicate ninos without suffixes: $duplicateSuffixes
       | ---- Latest duplicate relationship: $getLatestDuplicate""".stripMargin
  )
}
