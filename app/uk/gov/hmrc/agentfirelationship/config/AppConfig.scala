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

package uk.gov.hmrc.agentfirelationship.config

import java.net.URL
import java.net.URLDecoder
import javax.inject.Inject

import scala.concurrent.duration.Duration

import com.google.inject.ImplementedBy
import uk.gov.hmrc.agentfirelationship.models.BasicAuthentication
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {
  val appName: String
  val agentMappingBaseUrl: URL
  val desBaseUrl: URL
  val authBaseUrl: URL
  val copyCesaRelationshipFlag: Boolean
  val checkCesaRelationshipFlag: Boolean
  val desEnvironment: String
  val desAuthToken: String
  val oldStrideRole: String
  val newStrideRole: String
  val terminationStrideRole: String
  val inactiveRelationshipsShowLastDays: Duration
  val startupMongoQueryEnabled: Boolean
  def expectedAuth: BasicAuthentication
}

class AppConfigImpl @Inject() (config: ServicesConfig) extends AppConfig {

  override val appName: String = config.getString("appName")

  override val agentMappingBaseUrl = new URL(config.baseUrl("agent-mapping"))
  override val desBaseUrl: URL     = new URL(config.baseUrl("des"))
  override val authBaseUrl: URL    = new URL(config.baseUrl("auth"))

  override val copyCesaRelationshipFlag: Boolean  = config.getBoolean("features.copy-cesa-relationships")
  override val checkCesaRelationshipFlag: Boolean = config.getBoolean("features.check-cesa-relationships")

  override val desEnvironment: String        = config.getConfString("des.environment", "des.environment")
  override val desAuthToken: String          = config.getConfString("des.authorization-token", "des.authorization-token")
  override val oldStrideRole: String         = URLDecoder.decode(config.getString("old.auth.stride.role"), "utf-8")
  override val newStrideRole: String         = config.getString("new.auth.stride.role")
  override val terminationStrideRole: String = config.getString("termination.stride.enrolment")

  override val inactiveRelationshipsShowLastDays: Duration =
    Duration.create(config.getConfString("inactive-relationships.show-last-days", "30 days").replace("_", " "))

  def expectedAuth: BasicAuthentication = {
    val username = config.getString("agent-termination.username")
    val password = config.getString("agent-termination.password")

    BasicAuthentication(username, password)
  }

  val startupMongoQueryEnabled: Boolean = config.getBoolean("startupMongoQueryEnabled")

}
