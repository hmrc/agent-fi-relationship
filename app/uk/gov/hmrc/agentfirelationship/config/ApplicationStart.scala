/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.config

import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.agentfirelationship.services.RelationshipMongoService

import scala.concurrent.{ExecutionContext, Future}


//[APB-1829] Remove after released once. So it can go and fix up the existing records in DB
@Singleton
class ApplicationStart @Inject()(lifecycle: ApplicationLifecycle,
                                 mongoService: RelationshipMongoService,
                                 implicit val ec: ExecutionContext) {
  def start() = {
    Logger.info("Updating AFI relationships to have relationshipStatus set Active," +
      " remove this after records have been successfully updated")
    mongoService.addActiveRelationshipStatus().foreach {
      case true => Logger.warn("relationships have been updated to have relationshipStatus: Active")
      case false => ()
    }
  }

  lifecycle.addStopHook { () =>
    Future.successful(())
  }

  start()
}