package uk.gov.hmrc.agentfirelationship.support

import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.ServerProvider
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.Results

import scala.concurrent.Future

trait RelationshipActions extends ScalaFutures {

  this: Suite with ServerProvider =>

  val url = s"http://localhost:$port/agent-fi-relationship/relationships"

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def createRelationship(agentId: String, clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .put(Results.EmptyContent())

  def getRelationship(agentId: String, clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .get()

  def deleteRelationship(agentId: String, clientId: String, service: String): Future[WSResponse] =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .delete()


}
