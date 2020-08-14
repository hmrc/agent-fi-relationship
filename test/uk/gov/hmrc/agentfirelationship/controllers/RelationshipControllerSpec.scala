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

package uk.gov.hmrc.agentfirelationship.controllers

import java.net.URL

import org.mockito.ArgumentMatchers.{any, eq => eqs}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{Request, Result}
import play.api.test.Helpers
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthConnector
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.models.{BasicAuthentication, Relationship, RelationshipStatus}
import uk.gov.hmrc.agentfirelationship.services.{CesaRelationshipCopyService, RelationshipMongoService}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class RelationshipControllerSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockMongoService: RelationshipMongoService =
    mock[RelationshipMongoService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockCesaRelationship: CesaRelationshipCopyService = mock[CesaRelationshipCopyService]
  val mockAgentClientAuthConnector: AgentClientAuthConnector = new AgentClientAuthConnector(mockPlayAuthConnector)
  val testAppConfig = new AppConfig {
    override val appName: String = "agent-fi-relationship"
    override val agentMappingBaseUrl: URL = new URL("http://localhost:9999/agent-mapping")
    override val desBaseUrl: URL = new URL("http://localhost:9999/des")
    override val authBaseUrl: URL = new URL("http://localhost:9999/auth")
    override val copyCesaRelationshipFlag: Boolean = false
    override val checkCesaRelationshipFlag: Boolean = false
    override val desEnvironment: String = "des.env"
    override val desAuthToken: String = "des.auth.token"
    override val oldStrideRole: String = "maintain agent relationships"
    override val newStrideRole: String = "maintain_agent_relationships"
    override val terminationStrideRole: String = "caat"
    override val inactiveRelationshipsShowLastDays: Duration = Duration.create("30 days")
    override def expectedAuth: BasicAuthentication = BasicAuthentication("username", "password")
  }
  val mockControllerComponents = Helpers.stubControllerComponents()
  val oldStrideRole = "maintain agent relationships"
  val newStrideRole = "maintain_agent_relationships"
  val strideRoles: Seq[String] = Seq(oldStrideRole, newStrideRole)

  val controller = new RelationshipController(
    mockAuditService,
    mockMongoService,
    mockAgentClientAuthConnector,
    mockCesaRelationship,
    testAppConfig,
    mockControllerComponents
  )

  override def afterEach() {
    reset(mockMongoService, mockAuditService, mockPlayAuthConnector, mockCesaRelationship)
  }

  private type AfiAction =
    Option[TaxIdentifier] => Credentials => Future[Result]

  private def authStub(returnValue: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]]) =
    when(
      mockPlayAuthConnector
        .authorise(any[Predicate](), any[Retrieval[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]]]())(
          any[HeaderCarrier](),
          any[ExecutionContext]()))
      .thenReturn(returnValue)

  "RelationshipController" should {

    "return Status: OK when successfully finding a relationship" in {

      when(
        mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
          any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationship))

      val response = controller.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: NOT_FOUND for not finding data" in {

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List())

      when(
        mockMongoService
          .findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful List())

      val response = controller.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: CREATED for creating new record as a client" in {

      authStub(clientAffinityAndEnrolments)

      when(
        mockAuditService
          .sendCreateRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      when(mockMongoService.createRelationship(any[Relationship]())(any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List())

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(1))
        .createRelationship(any[Relationship]())(any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: CREATED for creating new record as an agent" in {

      authStub(agentAffinityAndEnrolmentsCreds)

      when(
        mockAuditService
          .sendCreateRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .createRelationship(any[Relationship]())(any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List())

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED

      verify(mockMongoService, times(1))
        .createRelationship(any[Relationship]())(any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "send an audit event if the relationship is successfully created" in {

      authStub(agentAffinityAndEnrolmentsCreds)

      when(
        mockAuditService
          .sendCreateRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .createRelationship(eqs(validTestRelationship))(any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List())

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)
      await(response)

      verify(mockAuditService, times(1))
        .sendCreateRelationshipEvent(any[AuditData]())(
          any[HeaderCarrier](),
          any[Request[Any]](),
          any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record, as a client" in {

      authStub(clientAffinityAndEnrolments)

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationship))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED

      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())(any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record, as an agent" in {

      authStub(agentAffinityAndEnrolmentsCreds)

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationship))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED

      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())(any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: CREATED when additional relationships are created as a client" in {
      authStub(clientAffinityAndEnrolments)

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationship))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())(any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: CREATED when additional relationships are created as an agent" in {
      authStub(agentAffinityAndEnrolmentsCreds)

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationship))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())(any[ExecutionContext]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: FORBIDDEN when logged in Agent ARN does not match given ARN when creating relationship" in {
      authStub(agentAffinityAndEnrolmentsCreds)

      val response =
        controller.createRelationship("JARN0000001", testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())(any[ExecutionContext]())

      verify(mockMongoService, times(0))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: FORBIDDEN when logged in Client NINO does not match given NINO when creating relationship" in {
      authStub(clientAffinityAndEnrolments)

      val response =
        controller.createRelationship(validTestArn, testService, "AB123456C")(fakeCreateRequest)

      status(response) shouldBe FORBIDDEN

      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())(any[ExecutionContext]())

      verify(mockMongoService, times(0))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: OK for deleting a record as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful true)

      when(
        mockAuditService
          .sendTerminatedRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "return Status: OK for deleting a record as an agent" in {
      authStub(agentAffinityAndEnrolmentsCreds)
      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful true)

      when(
        mockAuditService.sendTerminatedRelationshipEvent(any[AuditData]())(
          any[HeaderCarrier](),
          any[Request[Any]](),
          any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "return Status: OK for deleting a record as hmrc" in {
      authStub(strideEnrolmentsCred(oldStrideEnrolment))
      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful true)

      when(
        mockAuditService
          .sendHmrcLedDeleteRelationshipAuditEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "send an audit event if the relationship is successfully deleting" in {
      authStub(agentAffinityAndEnrolmentsCreds)
      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful true)

      when(
        mockAuditService
          .sendTerminatedRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)
      await(response)
      verify(mockAuditService, times(1))
        .sendTerminatedRelationshipEvent(any[AuditData]())(
          any[HeaderCarrier](),
          any[Request[Any]](),
          any[ExecutionContext]())
    }

    "return Status: NOT_FOUND for failing to delete a record as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(
        mockMongoService
          .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]()))
        .thenReturn(Future successful false)

      when(
        mockAuditService
          .sendTerminatedRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "return Status: NOT_FOUND for failing to delete a record as an agent" in {
      authStub(agentAffinityAndEnrolmentsCreds)
      when(
        mockMongoService
          .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]()))
        .thenReturn(Future successful false)

      when(
        mockAuditService
          .sendTerminatedRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "return Status: NOT_FOUND for failing to delete a record as hmrc" in {
      authStub(strideEnrolmentsCred(oldStrideEnrolment))
      when(
        mockMongoService
          .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]()))
        .thenReturn(Future successful false)

      when(
        mockAuditService
          .sendHmrcLedDeleteRelationshipAuditEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()))
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "return Status: FORBIDDEN when logged in Agent ARN does not match given ARN when deleting relationship" in {
      authStub(agentAffinityAndEnrolmentsCreds)

      val response =
        controller.terminateRelationship("JARN0000001", testService, validTestNINO)(fakeRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0))
        .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "return Status: FORBIDDEN when logged in Client NINO does not match given NINO when deleting relationship" in {
      authStub(clientAffinityAndEnrolments)

      val response = controller.terminateRelationship(validTestArn, testService, "AB123456C")(fakeRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0))
        .terminateRelationship(any[String](), any[String](), any[String]())(any[ExecutionContext]())
    }

    "return Status: OK for finding data via access control endpoint" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationship))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: NOT_FOUND for not finding data via access control endpoint" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))(
            any[ExecutionContext]()))
        .thenReturn(Future successful List())
      when(
        mockMongoService
          .findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful List())

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())(
          any[ExecutionContext]())
    }

    "return Status: OK with json body of all agent invitations with TERMINATED status" in {

      val agentAction: AfiAction = { implicit arn => implicit credentials =>
        Future successful Ok
      }

      authStub(agentAffinityAndEnrolmentsCreds)

      when(mockAgentClientAuthConnector.authorisedForAfi(strideRoles)(agentAction))
        .thenReturn(Future successful Ok)

      when(
        mockMongoService
          .findInactiveAgentRelationships(eqs(validTestArn))(any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationshipTerminated, validTestRelationshipCesa))

      val response = controller.findInactiveRelationships(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findInactiveAgentRelationships(any[String]())(any[ExecutionContext]())
    }

    "return Status: OK with json body of all agent invitations with ACTIVE status" in {

      val agentAction: AfiAction = { implicit arn => implicit credentials =>
        Future successful Ok
      }

      authStub(agentAffinityAndEnrolmentsCreds)

      when(mockAgentClientAuthConnector.authorisedForAfi(strideRoles)(agentAction))
        .thenReturn(Future successful Ok)

      when(
        mockMongoService
          .findActiveAgentRelationships(eqs(validTestArn))(any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationshipTerminated, validTestRelationshipCesa))

      val response = controller.findActiveRelationships(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findActiveAgentRelationships(any[String]())(any[ExecutionContext]())
    }

    "return Status: OK with json body of all client invitations with TERMINATED status" in {

      val action: AfiAction = { implicit arn => implicit credentials =>
        Future successful Ok
      }

      authStub(clientAffinityAndEnrolments)

      when(mockAgentClientAuthConnector.authorisedForAfi(strideRoles)(action))
        .thenReturn(Future successful Ok)

      when(
        mockMongoService
          .findInactiveClientRelationships(eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationshipTerminated, validTestRelationshipCesa))

      val response = controller.findInactiveRelationships(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findInactiveClientRelationships(any[String]())(any[ExecutionContext]())
    }

    "return Status: OK with json body of all client invitations with ACTIVE status" in {

      val action: AfiAction = { implicit arn => implicit credentials =>
        Future successful Ok
      }

      authStub(clientAffinityAndEnrolments)

      when(mockAgentClientAuthConnector.authorisedForAfi(strideRoles)(action))
        .thenReturn(Future successful Ok)

      when(
        mockMongoService
          .findActiveClientRelationships(eqs(validTestNINO))(any[ExecutionContext]()))
        .thenReturn(Future successful List(validTestRelationshipTerminated, validTestRelationshipCesa))

      val response = controller.findActiveRelationships(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findActiveClientRelationships(any[String]())(any[ExecutionContext]())
    }
  }
}
