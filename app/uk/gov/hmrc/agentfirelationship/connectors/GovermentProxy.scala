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

package uk.gov.hmrc.agentfirelationship.connectors


import java.net.URL
import javax.xml.parsers.SAXParserFactory

import com.codahale.metrics.MetricRegistry

import javax.inject.{Inject, Singleton}
import com.google.inject.name.Named
import com.kenshoo.play.metrics.Metrics
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpPost}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.ContentTypes.XML
import javax.xml.XMLConstants._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.Elem
import scala.xml.XML.withSAXParser
import org.apache.xerces.impl.Constants._


//todo write tests no time atm !
@Singleton
class GovernmentGatewayProxyConnector @Inject()(@Named("government-gateway-proxy-baseUrl") baseUrl: URL, httpPost: HttpPost, metrics: Metrics)
  extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private def path(method: String): String = new URL(baseUrl, s"/government-gateway-proxy/api/admin/$method").toString

  def getCredIdFor(arn: Arn)(implicit hc: HeaderCarrier): Future[String] = {
    monitor("ConsumedAPI-GGW-GsoAdminGetCredentialsForDirectEnrolments-POST") {
      httpPost.POSTString(path("GsoAdminGetCredentialsForDirectEnrolments"), GsoAdminGetCredentialsForDirectEnrolmentsXmlInput(arn), Seq(CONTENT_TYPE -> XML))
    }.map({ response =>
      val xml = toXmlElement(response.body)
      (xml \\ "CredentialIdentifier").headOption.map(_.text).getOrElse(throw RelationshipNotFound("INVALID_ARN"))
    })
  }
  private def GsoAdminGetCredentialsForDirectEnrolmentsXmlInput(arn: Arn): String =
    <GsoAdminGetCredentialsForDirectEnrolmentsXmlInput xmlns="urn:GSO-System-Services:external:1.67:GsoAdminGetCredentialsForDirectEnrolmentsXmlInput">
      <ServiceName>HMRC-AS-AGENT</ServiceName>
      <IdentifierSets>
        <IdentifierSet>
          <Identifiers>
            <Identifier IdentifierType="AgentReferenceNumber">{arn.value}</Identifier>
          </Identifiers>
        </IdentifierSet>
      </IdentifierSets>
    </GsoAdminGetCredentialsForDirectEnrolmentsXmlInput>.toString()

  private def toXmlElement(xmlString: String): Elem = {
    val factory = SAXParserFactory.newInstance("org.apache.xerces.jaxp.SAXParserFactoryImpl", this.getClass.getClassLoader)
    factory.setFeature(SAX_FEATURE_PREFIX + EXTERNAL_GENERAL_ENTITIES_FEATURE, false)
    factory.setFeature(SAX_FEATURE_PREFIX + EXTERNAL_PARAMETER_ENTITIES_FEATURE, false)
    factory.setFeature(XERCES_FEATURE_PREFIX + DISALLOW_DOCTYPE_DECL_FEATURE, true)
    factory.setFeature(FEATURE_SECURE_PROCESSING, true)
    withSAXParser(factory.newSAXParser())loadString xmlString
  }
}
case class RelationshipNotFound(errorCode: String) extends Exception(errorCode)