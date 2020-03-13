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

package uk.gov.hmrc.agentfirelationship.controllers.connectors

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{Result, Results}
import play.api.mvc.Results._
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentfirelationship.controllers._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class AuthConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockAuthConnector: AgentClientAuthConnector = new AgentClientAuthConnector(mockPlayAuthConnector)
  private type AfiAction =
    Option[TaxIdentifier] => Credentials => Future[Result]

  val agentAction: AfiAction = { implicit arn => implicit credentials =>
    Future successful Ok
  }
  val clientAction: AfiAction = { implicit nino => implicit credentials =>
    Future successful Ok
  }

  val strideAction: Future[Results.Status] = {
    Future successful Ok
  }

  val oldStrideId = "maintain agent relationships"
  val newStrideId = "maintain_agent_relationships"
  val terminateStrideId = "caat"

  val strideRoles: Seq[String] = Seq(oldStrideId, newStrideId)

  override def beforeEach(): Unit = reset(mockPlayAuthConnector)

  private def authStubGen[T](returnValue: Future[T]) =
    when(
      mockPlayAuthConnector.authorise(any[Predicate](), any[Retrieval[T]]())(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(returnValue)

  "authorisedForAfi" should {
    "return OK for an Agent with HMRC-AS-AGENT enrolment" in {
      authStubGen(agentAffinityAndEnrolmentsCreds)

      val response: Result =
        await(mockAuthConnector.authorisedForAfi(strideRoles)(agentAction))

      status(response) shouldBe OK
    }

    "return OK for a Client with HMRC-NI enrolment" in {
      authStubGen(clientAffinityAndEnrolments)

      val response: Result =
        await(mockAuthConnector.authorisedForAfi(strideRoles)(clientAction))

      status(response) shouldBe OK
    }

    "return OK for a stride authenticated user" in {
      authStubGen(strideEnrolmentsCred(oldStrideEnrolment))

      val response: Result =
        await(mockAuthConnector.authorisedForAfi(strideRoles)(agentAction))

      status(response) shouldBe OK
    }

    "return OK for a new stride authenticated user" in {
      authStubGen(strideEnrolmentsCred(newStrideEnrolment))

      val response: Result =
        await(mockAuthConnector.authorisedForAfi(strideRoles)(agentAction))

      status(response) shouldBe OK
    }

    "return FORBIDDEN for an Agent with no enrolment" in {
      authStubGen(agentNoEnrolments)

      val response: Result =
        await(mockAuthConnector.authorisedForAfi(strideRoles)(agentAction))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN for a Client with no enrolment" in {
      authStubGen(clientNoEnrolments)

      val response: Result =
        await(mockAuthConnector.authorisedForAfi(strideRoles)(clientAction))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN when auth fails to return retrieve enrolments and affinity group" in {
      authStubGen(neitherHaveAffinityOrEnrolment)

      val response: Result =
        await(mockAuthConnector.authorisedForAfi(strideRoles)(agentAction))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN when auth throws an error" in {
      authStubGen(failedAuthStub)

      val response: Result =
        await(mockAuthConnector.authorisedForAfi(strideRoles)(agentAction))

      status(response) shouldBe FORBIDDEN
    }
  }

  "onlyStride" should {
    "return 200 for successful stride authentication" in {
      authStubGen(onlyStride(terminateStrideEnrolment))

      val response: Result = await(mockAuthConnector.onlyStride(terminateStrideId)(_ => strideAction))

      status(response) shouldBe OK
    }

    "return 401 for unauthorised stride authentication" in {
      authStubGen(onlyStride(newStrideEnrolment))

      val response: Result = await(mockAuthConnector.onlyStride(terminateStrideId)(_ => strideAction))

      status(response) shouldBe UNAUTHORIZED
    }

    "return 403 for unsuccessful stride authentication" in {
      authStubGen(failedAuthStub)

      val response: Result = await(mockAuthConnector.onlyStride(terminateStrideId)(_ => strideAction))

      status(response) shouldBe FORBIDDEN
    }

  }
}
