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

package agentfirelationship.support

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.util.Base64

import scala.concurrent.Future

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.http.HeaderNames

trait RelationshipActions extends ScalaFutures {

  this: GuiceOneServerPerSuite =>

  val baseUrl = s"http://localhost:$port/agent-fi-relationship"
  val url     = s"$baseUrl/relationships"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def createRelationship(
      agentId: String,
      clientId: String,
      service: String,
      startDate: LocalDateTime
  ): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .withHttpHeaders("Authorization" -> "Bearer XYZ")
      .put(Json.obj("startDate" -> startDate))

  def getRelationship(agentId: String, clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .withHttpHeaders("Authorization" -> "Bearer XYZ")
      .get()

  def terminateRelationship(agentId: String, clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .withHttpHeaders("Authorization" -> "Bearer XYZ")
      .delete()

  def terminateClientRelationships(clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/service/$service/clientId/$clientId")
      .withHttpHeaders("Authorization" -> "Bearer XYZ")
      .delete()

  def getInactiveRelationships: Future[WSResponse] =
    wsClient
      .url(s"$url/inactive")
      .withHttpHeaders("Authorization" -> "Bearer XYZ")
      .get()

  def basicAuth(string: String): String = Base64.getEncoder.encodeToString(string.getBytes(UTF_8))

  def removeAFIRelationshipsForAgent(arn: String): Future[WSResponse] =
    wsClient
      .url(s"$baseUrl/agent/$arn/terminate")
      .withHttpHeaders(HeaderNames.authorisation -> s"Basic ${basicAuth("username:password")}")
      .delete()

  def hasActiveLegacySaRelationship(utr: String): Future[WSResponse] =
    wsClient
      .url(s"$url/active-legacy-sa/utr/$utr")
      .withHttpHeaders("Authorization" -> "Bearer XYZ")
      .get()
}
