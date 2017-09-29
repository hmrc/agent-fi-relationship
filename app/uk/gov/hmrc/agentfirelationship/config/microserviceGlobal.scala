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

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import play.api.{Application, Configuration}
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal

@Singleton
class ControllerConfiguration @Inject()(configuration: Configuration) extends ControllerConfig {
  lazy val controllerConfigs: Config = configuration.underlying.as[Config]("controllers")
}

object ControllerConfigurationObject {
  lazy val controllerConfigsClass = new ControllerConfiguration(Configuration.load(play.Environment.simple().underlying()))
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector

  override def controllerNeedsAuditing(controllerName: String): Boolean = ControllerConfigurationObject.controllerConfigsClass.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String): Boolean = ControllerConfigurationObject.controllerConfigsClass.paramsForController(controllerName).needsLogging
}


object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None
}
