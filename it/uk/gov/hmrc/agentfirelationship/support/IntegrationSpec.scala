package uk.gov.hmrc.agentfirelationship.support

import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import play.api.http.Status

trait IntegrationSpec extends AnyFeatureSpec with GivenWhenThen with Matchers with Eventually with Status {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(5, Seconds), interval = Span(5, Millis))

}
