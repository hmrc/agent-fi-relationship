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

package uk.gov.hmrc.agentfirelationship

import java.time.LocalDateTime

import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.{Active, Terminated}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
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
  val validTestArn = "TARN0000001"
  val testCredId = "q213"
  val testService = "PERSONAL-INCOME-RECORD"
  val validTestNINO = "AE123456C"
  val validTestNINO2 = "AB123456A"

  val GGcredentials = Credentials("someId", "GovernmentGateway")
  val PAcredentials = Credentials("someId", "PrivilegedApplication")

  val validTestRelationship: Relationship =
    Relationship(Arn(validTestArn), testService, validTestNINO, Some(Active), testResponseDate, None)
  val validTestRelationshipTerminated: Relationship =
    Relationship(Arn(validTestArn), testService, validTestNINO2, Some(Terminated), testResponseDate, None)
  val validTestRelationshipCesa: Relationship = Relationship(
    Arn(validTestArn),
    testService,
    validTestNINO,
    Some(Terminated),
    testResponseDate,
    None,
    fromCesa = Some(true))

  val agentEnrolment = Set(
    Enrolment(
      "HMRC-AS-AGENT",
      Seq(EnrolmentIdentifier("AgentReferenceNumber", validTestArn)),
      state = "",
      delegatedAuthRule = None))

  val clientEnrolment = Set(
    Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", validTestNINO)), state = "", delegatedAuthRule = None))

  val oldStrideEnrolment = Set(Enrolment("maintain agent relationships"))
  val newStrideEnrolment = Set(Enrolment("maintain_agent_relationships"))

  val clientAffinityAndEnrolments: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future successful new ~(new ~(Some(AffinityGroup.Individual), Enrolments(clientEnrolment)), Some(GGcredentials))

  val agentAffinityAndEnrolmentsCreds: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future successful new ~(new ~(Some(AffinityGroup.Agent), Enrolments(agentEnrolment)), Some(GGcredentials))

  val strideEnrolmentsCred: Set[Enrolment] => Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    strideEnrolments =>
      Future successful new ~(new ~(Some(AffinityGroup.Agent), Enrolments(strideEnrolments)), Some(PAcredentials))

  val clientNoEnrolments: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future successful new ~(new ~(Some(AffinityGroup.Individual), Enrolments(Set.empty)), Some(GGcredentials))

  val agentNoEnrolments: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future successful new ~(new ~(Some(AffinityGroup.Agent), Enrolments(Set.empty)), Some(GGcredentials))

  val neitherHaveAffinityOrEnrolment: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future successful new ~(new ~(None, Enrolments(Set.empty)), Some(GGcredentials))

  val failedAuthStub: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future failed new InsufficientEnrolments
}
