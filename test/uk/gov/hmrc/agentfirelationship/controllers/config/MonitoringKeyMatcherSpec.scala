/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.controllers.config

import app.Routes
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentfirelationship.wiring.{KeyToPatternMappingFromRoutes, MonitoringKeyMatcher}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext

class MonitoringKeyMatcherSpec extends UnitSpec {

  implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()

  private implicit val hc = HeaderCarrier()
  private implicit val ec = ExecutionContext.global

  val routes = app.injector.instanceOf[Routes]

  "MonitoringKeyMatcher" should {

    "prepare patterns and variables" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] = Seq()
      }
      tested.preparePatternAndVariables("""/some/test/:service/:clientId/:test1""") shouldBe (
        (
          "^.*/some/test/([^/]+)/([^/]+)/([^/]+)$",
          Seq("{service}", "{clientId}", "{test1}")))
      tested.preparePatternAndVariables("""/some/test/:service/:clientId/:test1/""") shouldBe (
        (
          "^.*/some/test/([^/]+)/([^/]+)/([^/]+)/$",
          Seq("{service}", "{clientId}", "{test1}")))
      tested.preparePatternAndVariables("""/some/test/:service/::clientId/:test1/""") shouldBe (
        (
          "^.*/some/test/([^/]+)/([^/]+)/([^/]+)/$",
          Seq("{service}", "{:clientId}", "{test1}")))
      tested.preparePatternAndVariables("""/some/test/:service/clientId/:test1/""") shouldBe (
        (
          "^.*/some/test/([^/]+)/clientId/([^/]+)/$",
          Seq("{service}", "{test1}")))
    }

    "throw exception if duplicate variable name in pattern" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] = Seq()
      }
      an[IllegalArgumentException] shouldBe thrownBy {
        tested.preparePatternAndVariables("""/some/test/:service/:clientId/:service""")
      }
    }

    "match value to known pattern and produce key with placeholders replaced" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] = Seq(
          "A-{service}"            -> """/some/test/:service/:clientId""",
          "B-{service}"            -> """/test/:service/bar/some""",
          "C-{service}"            -> """/test/:service/bar""",
          "D-{service}"            -> """/test/:service/""",
          "E-{clientId}-{service}" -> """/test/:service/:clientId"""
        )
      }
      tested.findMatchingKey("http://www.tax.service.hmrc.gov.uk/test/ME/bar") shouldBe Some("C-ME")
      tested.findMatchingKey("http://www.tax.service.hmrc.gov.uk/test/ME/bar/some") shouldBe Some("B-ME")
      tested.findMatchingKey("http://www.tax.service.hmrc.gov.uk/test/ME") shouldBe None
      tested.findMatchingKey("/some/test/ME/12616276") shouldBe Some("A-ME")
      tested.findMatchingKey("http://www.tax.service.hmrc.gov.uk/test/ME/TOO") shouldBe Some("E-TOO-ME")
      tested.findMatchingKey("/test/ME/TOO/") shouldBe None
    }

    "match URI to known pattern and produce key with placeholders replaced" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] = Seq(
          "relationships-{service}"        -> "/relationships/agent/:arn/service/:service/client/:clientId",
          "check-PIR"                      -> "/relationships/PERSONAL-INCOME-RECORD/agent/:arn/client/:clientId",
          "check-AFI"                      -> "/relationships/afi/agent/:arn/client/:clientId",
          "client-relationships-{service}" -> "/relationships/service/:service/clientId/:clientId"
        )
      }
      tested.findMatchingKey(
        "http://agent-fi-relationships.protected.mdtp/relationships/agent/ARN123456/service/PERSONAL-INCOME-RECORD/client/GHZ8983HJ") shouldBe Some(
        "relationships-PERSONAL-INCOME-RECORD")
      tested.findMatchingKey(
        "http://agent-fi-relationships.protected.mdtp/relationships/PERSONAL-INCOME-RECORD/agent/ARN123456/client/GHZ8983HJ") shouldBe Some(
        "check-PIR")
      tested.findMatchingKey(
        "http://agent-fi-relationships.protected.mdtp/relationships/afi/agent/ARN123456/client/GHZ8983HJ") shouldBe Some(
        "check-AFI")
      tested.findMatchingKey(
        "http://agent-fi-relationships.protected.mdtp/relationships/service/PERSONAL-INCOME-RECORD/clientId/GHZ8983HJ") shouldBe Some(
        "client-relationships-PERSONAL-INCOME-RECORD")
    }

    "parse Routes and produce monitoring key-pattern pairs" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] =
          KeyToPatternMappingFromRoutes(routes, Set("service"))
      }
      tested.findMatchingKey(
        "http://agent-fi-relationships.protected.mdtp/relationships/agent/ARN123456/service/PERSONAL-INCOME-RECORD/client/GHZ8983HJ") shouldBe Some(
        "__relationships__agent__:__service__PERSONAL-INCOME-RECORD__client__:")
      tested.findMatchingKey(
        "http://agent-fi-relationships.protected.mdtp/relationships/PERSONAL-INCOME-RECORD/agent/ARN123456/client/GHZ8983HJ") shouldBe Some(
        "__relationships__PERSONAL-INCOME-RECORD__agent__:__client__:")
      tested.findMatchingKey(
        "http://agent-fi-relationships.protected.mdtp/relationships/afi/agent/ARN123456/client/GHZ8983HJ") shouldBe Some(
        "__relationships__afi__agent__:__client__:")
      tested.findMatchingKey(
        "http://agent-fi-relationships.protected.mdtp/relationships/service/PERSONAL-INCOME-RECORD/clientId/GHZ8983HJ") shouldBe Some(
        "__relationships__service__PERSONAL-INCOME-RECORD__clientId__:")
    }
  }
}
