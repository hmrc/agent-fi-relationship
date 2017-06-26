package uk.gov.hmrc.agentfirelationship.support

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status
import play.api.libs.ws.WSClient

trait ActionsSupport extends IntegrationSpec with GuiceOneServerPerSuite with Status with ScalaFutures {

  val url = "http://localhost:9427/agent-fi-relationship/relationships"

  override lazy val port: Int = 9427

  val wsClient = app.injector.instanceOf[WSClient]

}
