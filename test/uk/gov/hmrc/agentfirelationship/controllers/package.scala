/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship

import java.time.LocalDateTime

import play.api.libs.json.{ JsObject, Json }
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.{ Active, Terminated }
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

package object controllers {
  implicit val hc = new HeaderCarrier
  val testResponseDate = LocalDateTime.now

  val fakeRequest = FakeRequest("GET", "")

  val fakeCreateRequest: FakeRequest[JsObject] = FakeRequest("PUT", "/")
    .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
    .withBody(Json.obj("startDate" -> testResponseDate))

  val saAgentRef = SaAgentReference("T1113T")
  val saAgentRef2 = SaAgentReference("T1123T")
  val saAgentRef3 = SaAgentReference("T1133T")
  val validTestArn = "AARN1234567"
  val testCredId = "q213"
  val testService = "PERSONAL-INCOME-RECORD"
  val validTestNINO = "AE123456C"

  val validTestRelationship: Relationship = Relationship(Arn(validTestArn), testService, validTestNINO, Some(Active), testResponseDate, None)
  val validTestRelationshipCesa: Relationship = Relationship(Arn(validTestArn), testService, validTestNINO, Some(Terminated), testResponseDate, None, fromCesa = Some(true))

  val agentEnrolment = Set(
    Enrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", validTestArn)),
      state = "", delegatedAuthRule = None))

  val clientEnrolment = Set(
    Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", validTestNINO)), state = "", delegatedAuthRule = None))

  val clientAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Individual), Enrolments(clientEnrolment))

  val agentAffinityAndEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(agentEnrolment))

  val clientNoEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Individual), Enrolments(Set.empty[Enrolment]))

  val agentNoEnrolments: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](Some(AffinityGroup.Agent), Enrolments(Set.empty[Enrolment]))

  val neitherHaveAffinityOrEnrolment: Future[~[Option[AffinityGroup], Enrolments]] =
    Future successful new ~[Option[AffinityGroup], Enrolments](None, Enrolments(Set.empty[Enrolment]))

  val failedAuthStub: Future[~[Option[AffinityGroup], Enrolments]] =
    Future failed new InsufficientEnrolments
}
