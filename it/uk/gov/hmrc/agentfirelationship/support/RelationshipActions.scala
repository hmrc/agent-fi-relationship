package uk.gov.hmrc.agentfirelationship.support

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime
import java.util.Base64

import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.ServerProvider
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.http.HeaderNames

import scala.concurrent.Future

trait RelationshipActions extends ScalaFutures {

  this: Suite with ServerProvider =>

  val baseUrl = s"http://localhost:$port/agent-fi-relationship"
  val url = s"$baseUrl/relationships"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def createRelationship(agentId: String, clientId: String, service: String, startDate: LocalDateTime): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .put(Json.obj("startDate" -> startDate))

  def getRelationship(agentId: String, clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .get()

  def terminateRelationship(agentId: String, clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .delete()

  def terminateClientRelationships(clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/service/$service/clientId/$clientId")
      .delete()

  def getInactiveRelationships: Future[WSResponse] =
    wsClient
      .url(s"$url/inactive")
      .get()

  def basicAuth(string: String): String = Base64.getEncoder.encodeToString(string.getBytes(UTF_8))

  def removeAFIRelationshipsForAgent(arn: String): Future[WSResponse] =
    wsClient
      .url(s"$baseUrl/agent/$arn/terminate")
    .withHttpHeaders(HeaderNames.authorisation -> s"Basic ${basicAuth("username:password")}")
      .delete()
}
