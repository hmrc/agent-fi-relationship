/*
 * Copyright 2019 HM Revenue & Customs
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

import java.net.{URL, URLDecoder}

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.name.{Named, Names}
import com.typesafe.config.Config
import javax.inject.{Inject, Provider, Singleton}

import org.slf4j.MDC
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.agentfirelationship.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.ws.WSHttp

import scala.concurrent.duration.Duration

class MicroserviceModule(val environment: Environment,
                         val configuration: Configuration)
    extends AbstractModule
    with ServicesConfig {

  override val runModeConfiguration: Configuration = configuration
  override protected def mode = environment.mode

  def configure(): Unit = {
    val appName = "agent-fi-relationship"

    val loggerDateFormat: Option[String] =
      configuration.getString("logger.json.dateformat")
    Logger.info(
      s"Starting microservice : $appName : in mode : ${environment.mode}")
    MDC.put("appName", appName)
    loggerDateFormat.foreach(str => MDC.put("logger.json.dateformat", str))

    bindProperty("appName")

    bind(classOf[HttpGet]).to(classOf[HttpVerbs])
    bind(classOf[HttpPost]).to(classOf[HttpVerbs])
    bind(classOf[AuthConnector]).to(classOf[MicroserviceAuthConnector])

    bindBaseUrl("agent-mapping")
    bindBaseUrl("des")
    bindBaseUrl("auth")

    bindBooleanProperty("features.copy-cesa-relationships")
    bindBooleanProperty("features.check-cesa-relationships")
    bindProperty("des.environment", "des.environment")
    bindProperty("des.authorizationToken", "des.authorization-token")
    bindProperty("auth.stride.role", URLDecoder.decode(_, "utf-8"))
    bindServiceConfigProperty[Duration]("inactive-relationships.show-last-days")
  }

  private def bindBaseUrl(serviceName: String) =
    bind(classOf[URL])
      .annotatedWith(Names.named(s"$serviceName-baseUrl"))
      .toProvider(new BaseUrlProvider(serviceName))

  private class BaseUrlProvider(serviceName: String) extends Provider[URL] {
    override lazy val get = new URL(baseUrl(serviceName))
  }

  private def bindProperty(objectName: String, propertyName: String) =
    bind(classOf[String])
      .annotatedWith(Names.named(objectName))
      .toProvider(new PropertyProvider2param(propertyName))

  private class PropertyProvider2param(confKey: String)
      extends Provider[String] {
    override lazy val get =
      getConfString(confKey,
                    throw new IllegalStateException(
                      s"No value found for configuration property $confKey"))
  }

  private def bindProperty(propertyName: String,
                           mapFx: String => String = identity) =
    bind(classOf[String])
      .annotatedWith(Names.named(propertyName))
      .toProvider(new PropertyProvider(propertyName, mapFx))

  private class PropertyProvider(confKey: String, mapFx: String => String)
      extends Provider[String] {
    override lazy val get = configuration
      .getString(confKey)
      .map(mapFx)
      .getOrElse(throw new IllegalStateException(
        s"No value found for configuration property $confKey"))
  }

  private def bindBooleanProperty(propertyName: String) =
    bind(classOf[Boolean])
      .annotatedWith(Names.named(propertyName))
      .toProvider(new BooleanPropertyProvider(propertyName))

  private class BooleanPropertyProvider(confKey: String)
      extends Provider[Boolean] {
    override lazy val get: Boolean = configuration
      .getBoolean(confKey)
      .getOrElse(
        throw new IllegalStateException(
          s"No value found for configuration property $confKey"))
  }

  import com.google.inject.binder.ScopedBindingBuilder
  import com.google.inject.name.Names.named

  import scala.reflect.ClassTag

  private def bindServiceConfigProperty[A](propertyName: String)(
      implicit classTag: ClassTag[A],
      ct: ServiceConfigPropertyType[A]): ScopedBindingBuilder =
    ct.bindServiceConfigProperty(classTag.runtimeClass.asInstanceOf[Class[A]])(
      propertyName)

  sealed trait ServiceConfigPropertyType[A] {
    def bindServiceConfigProperty(clazz: Class[A])(
        propertyName: String): ScopedBindingBuilder
  }

  object ServiceConfigPropertyType {

    implicit val durationServiceConfigProperty
      : ServiceConfigPropertyType[Duration] =
      new ServiceConfigPropertyType[Duration] {
        def bindServiceConfigProperty(clazz: Class[Duration])(
            propertyName: String): ScopedBindingBuilder =
          bind(clazz)
            .annotatedWith(named(s"$propertyName"))
            .toProvider(new DurationServiceConfigPropertyProvider(propertyName))

        private class DurationServiceConfigPropertyProvider(
            propertyName: String)
            extends Provider[Duration] {
          override lazy val get = getConfDuration(
            propertyName,
            throw new RuntimeException(
              s"No service configuration value found for $propertyName"))
        }

        def getConfDurationCustom(confKey: String, defDur: => Duration) =
          runModeConfiguration
            .getString(s"$rootServices.$confKey")
            .orElse(runModeConfiguration.getString(s"$services.$confKey"))
            .orElse(runModeConfiguration.getString(s"$playServices.$confKey")) match {
            case Some(s) => Duration.create(s.replace("_", " "))
            case None    => defDur
          }
      }
  }

}

@Singleton
class HttpVerbs @Inject()(val auditConnector: AuditConnector,
                          @Named("appName") val appName: String,
                          val config: Configuration,
                          val actorSystem: ActorSystem)
    extends HttpGet
    with HttpPost
    with HttpPut
    with HttpPatch
    with HttpDelete
    with WSHttp
    with HttpAuditing {
  override val hooks = Seq(AuditingHook)
  override val configuration: Option[Config] = Some(config.underlying)
}
