package uk.gov.hmrc.agentfirelationship.support

import org.scalatest.Suite
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.ServerProvider
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.Results

trait RelationshipActions extends ScalaFutures {

  this: Suite with ServerProvider =>

  val url = s"http://localhost:$port/agent-fi-relationship/relationships"

  val wsClient = app.injector.instanceOf[WSClient]

  def createRelationship ( agentId: String, clientId: String, service: String ): WSResponse =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .post(Results.EmptyContent())
      .futureValue

  def getRelationship ( agentId: String, clientId: String, service: String ): WSResponse =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .get()
      .futureValue

  def deleteRelationship ( agentId: String, clientId: String, service: String ): WSResponse =
    wsClient
      .url(s"$url/agent/$agentId/service/$service/client/$clientId")
      .delete()
      .futureValue


}
