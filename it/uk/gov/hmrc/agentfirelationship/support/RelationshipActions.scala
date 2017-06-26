package uk.gov.hmrc.agentfirelationship.support

import org.scalatest.Suite
import play.api.libs.ws.WSResponse
import play.api.mvc.Results


trait RelationshipActions extends ActionsSupport {

  this: Suite =>

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
