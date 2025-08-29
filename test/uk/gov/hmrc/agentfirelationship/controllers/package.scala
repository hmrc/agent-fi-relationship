/*
 * Copyright 2023 HM Revenue & Customs
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

import scala.concurrent.Future

import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.agentfirelationship.models.Arn
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Terminated
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.SaAgentReference
import uk.gov.hmrc.http.HeaderCarrier

package object controllers {
  implicit val hc: HeaderCarrier      = new HeaderCarrier
  val testResponseDate: LocalDateTime = LocalDateTime.now

  val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "")

  val fakeCreateRequest: FakeRequest[JsObject] = FakeRequest("PUT", "/")
    .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json")
    .withBody(Json.obj("startDate" -> testResponseDate))

  val saAgentRef: SaAgentReference  = SaAgentReference("T1113T")
  val saAgentRef2: SaAgentReference = SaAgentReference("T1123T")
  val saAgentRef3: SaAgentReference = SaAgentReference("T1133T")
  val validTestArn: String          = "TARN0000001"
  val validTestArn2: String         = "TARN0000002"
  val testCredId: String            = "q213"
  val testService: String           = "PERSONAL-INCOME-RECORD"
  val validTestNINO: String         = "AE123456C"
  val validTestNINO2: String        = "AB123456A"

  val GGcredentials: Credentials = Credentials("someId", "GovernmentGateway")
  val PAcredentials: Credentials = Credentials("someId", "PrivilegedApplication")

  val validTestRelationship: Relationship =
    Relationship(Arn(validTestArn), testService, validTestNINO, Some(Active), testResponseDate, None)
  val validTestRelationshipTerminated: Relationship =
    Relationship(Arn(validTestArn), testService, validTestNINO2, Some(Terminated), testResponseDate, None)
  val validTestRelationshipCesa: Relationship =
    Relationship(
      Arn(validTestArn),
      testService,
      validTestNINO,
      Some(Terminated),
      testResponseDate,
      None,
      fromCesa = Some(true)
    )

  val agentEnrolment: Set[Enrolment] = Set(
    Enrolment(
      "HMRC-AS-AGENT",
      Seq(EnrolmentIdentifier("AgentReferenceNumber", validTestArn)),
      state = "",
      delegatedAuthRule = None
    )
  )

  val clientEnrolment: Set[Enrolment] = Set(
    Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", validTestNINO)), state = "", delegatedAuthRule = None)
  )

  val oldStrideEnrolment: Set[Enrolment]       = Set(Enrolment("maintain agent relationships"))
  val newStrideEnrolment: Set[Enrolment]       = Set(Enrolment("maintain_agent_relationships"))
  val terminateStrideEnrolment: Set[Enrolment] = Set(Enrolment("caat"))

  val clientAffinityAndEnrolments: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future.successful(new ~(new ~(Some(AffinityGroup.Individual), Enrolments(clientEnrolment)), Some(GGcredentials)))

  val agentAffinityAndEnrolmentsCreds: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future.successful(new ~(new ~(Some(AffinityGroup.Agent), Enrolments(agentEnrolment)), Some(GGcredentials)))

  val strideEnrolmentsCred: Set[Enrolment] => Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    strideEnrolments =>
      Future.successful(new ~(new ~(Some(AffinityGroup.Agent), Enrolments(strideEnrolments)), Some(PAcredentials)))

  val clientNoEnrolments: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future.successful(new ~(new ~(Some(AffinityGroup.Individual), Enrolments(Set.empty)), Some(GGcredentials)))

  val agentNoEnrolments: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future.successful(new ~(new ~(Some(AffinityGroup.Agent), Enrolments(Set.empty)), Some(GGcredentials)))

  val neitherHaveAffinityOrEnrolment: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future.successful(new ~(new ~(None, Enrolments(Set.empty)), Some(GGcredentials)))

  val failedAuthStub: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]] =
    Future.failed(new InsufficientEnrolments)

  val onlyStride: Set[Enrolment] => Future[Enrolments] =
    strideEnrolments => Future.successful(Enrolments(strideEnrolments))

  val onlyStrideFail: Future[Enrolments] =
    Future.failed(new UnsupportedAuthProvider)
}
