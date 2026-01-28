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

import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.google.inject.ImplementedBy
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.mongo.lock.LockService
import uk.gov.hmrc.mongo.lock.MongoLockRepository

@ImplementedBy(classOf[MongoLockServiceImpl])
trait MongoLockService {

  def relationshipLock[T](jobName: String)(
      body: => Future[T]
  )(implicit ec: ExecutionContext, appConfig: AppConfig): Future[Option[T]]

}

@Singleton
class MongoLockServiceImpl @Inject() (lockRepository: MongoLockRepository) extends MongoLockService {

  def relationshipLock[T](
      jobName: String
  )(body: => Future[T])(implicit ec: ExecutionContext, appConfig: AppConfig): Future[Option[T]] = {
    val lockService = LockService(
      lockRepository,
      lockId = s"relationship-lock-$jobName",
      ttl = appConfig.relationshipLockTTL.seconds
    )
    Mdc.preservingMdc {
      lockService.withLock(body)
    }
  }
}
