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

package uk.gov.hmrc.agentfirelationship.controllers.connectors

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthConnector
import uk.gov.hmrc.agentfirelationship.controllers._
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AuthConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockAuthConnector: AgentClientAuthConnector = new AgentClientAuthConnector {
    override def authConnector: PlayAuthConnector = mockPlayAuthConnector
  }

  private type AfiAction = Request[AnyContent] => TaxIdentifier => Future[Result]

  val agentAction: AfiAction = { implicit request => implicit arn => Future successful Ok }
  val clientAction: AfiAction = { implicit request => implicit nino => Future successful Ok }

  override def beforeEach(): Unit = reset(mockPlayAuthConnector)

  private def authStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any(), any())).thenReturn(returnValue)

  "authorisedForAfi" should {
    "return OK for an Agent with HMRC-AS-AGENT enrolment" in {
      authStub(agentAffinityAndEnrolments)

      val response: Result = await(mockAuthConnector.authorisedForAfi(agentAction).apply(FakeRequest()))

      status(response) shouldBe OK
    }

    "return OK for a Client with HMRC-NI enrolment" in {
      authStub(clientAffinityAndEnrolments)

      val response: Result = await(mockAuthConnector.authorisedForAfi(clientAction).apply(FakeRequest()))

      status(response) shouldBe OK
    }

    "return FORBIDDEN for an Agent with no enrolment" in {
      authStub(agentNoEnrolments)

      val response: Result = await(mockAuthConnector.authorisedForAfi(agentAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN for a Client with no enrolment" in {
      authStub(clientNoEnrolments)

      val response: Result = await(mockAuthConnector.authorisedForAfi(clientAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN when auth fails to return retrieve enrolments and affinity group" in {
      authStub(neitherHaveAffinityOrEnrolment)

      val response: Result = await(mockAuthConnector.authorisedForAfi(agentAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN when auth throws an error" in {
      authStub(failedAuthStub)

      val response: Result = await(mockAuthConnector.authorisedForAfi(agentAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }
  }
}
