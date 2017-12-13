package uk.gov.hmrc.agentfirelationship.support

import java.time.LocalDateTime

import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.ServerProvider
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.Future

trait RelationshipActions extends ScalaFutures {

  this: Suite with ServerProvider =>

  val url = s"http://localhost:$port/agent-fi-relationship/relationships"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def createRelationship(agentId: String, clientId: String, service: String, startDate: LocalDateTime): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .put(Json.obj("startDate"-> startDate))

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
}
