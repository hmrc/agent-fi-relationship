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

package uk.gov.hmrc.agentfirelationship.controllers

import java.time.LocalDateTime

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.mockito.ArgumentMatchers.{ eq => eqs }
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results._
import play.api.test.Helpers
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.AuditData
import uk.gov.hmrc.agentfirelationship.audit.AuditService
import uk.gov.hmrc.agentfirelationship.config.AppConfig
import uk.gov.hmrc.agentfirelationship.connectors.AgentClientAuthConnector
import uk.gov.hmrc.agentfirelationship.connectors.DesConnector
import uk.gov.hmrc.agentfirelationship.models.Relationship
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus
import uk.gov.hmrc.agentfirelationship.models.RelationshipStatus.Active
import uk.gov.hmrc.agentfirelationship.repository.RelationshipMongoRepository
import uk.gov.hmrc.agentfirelationship.services.AgentClientAuthorisationService
import uk.gov.hmrc.agentfirelationship.services.CesaRelationshipCopyService
import uk.gov.hmrc.agentfirelationship.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier

class RelationshipControllerSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  val mockMongoService: RelationshipMongoRepository =
    mock[RelationshipMongoRepository]
  val mockAuditService: AuditService                         = mock[AuditService]
  val mockPlayAuthConnector: PlayAuthConnector               = mock[PlayAuthConnector]
  val mockCesaRelationship: CesaRelationshipCopyService      = mock[CesaRelationshipCopyService]
  val mockDesConnector: DesConnector                         = mock[DesConnector]
  val mockAgentClientAuthConnector: AgentClientAuthConnector = new AgentClientAuthConnector(mockPlayAuthConnector)
  val testIrvArn                                             = "TARN0000001"
  val mockAcaService: AgentClientAuthorisationService        = mock[AgentClientAuthorisationService]
  val mockAppConfig: AppConfig                               = mock[AppConfig]
  val mockControllerComponents                               = Helpers.stubControllerComponents()
  val oldStrideRole                                          = "maintain agent relationships"
  val newStrideRole                                          = "maintain_agent_relationships"
  val strideRoles: Seq[String]                               = Seq(oldStrideRole, newStrideRole)
  when(mockAppConfig.oldStrideRole).thenReturn(oldStrideRole)
  when(mockAppConfig.newStrideRole).thenReturn(newStrideRole)

  val controller = new RelationshipController(
    mockAuditService,
    mockMongoService,
    mockAgentClientAuthConnector,
    mockCesaRelationship,
    mockAcaService,
    mockDesConnector,
    mockAppConfig,
    mockControllerComponents
  )

  override def afterEach(): Unit = {
    reset(mockMongoService)
    reset(mockAuditService)
    reset(mockPlayAuthConnector)
    reset(mockCesaRelationship)
    reset(mockAcaService)
  }

  private type AfiAction =
    Option[TaxIdentifier] => Credentials => Future[Result]

  private def authStub(returnValue: Future[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]]) =
    when(
      mockPlayAuthConnector
        .authorise(any[Predicate](), any[Retrieval[~[~[Option[AffinityGroup], Enrolments], Option[Credentials]]]]())(
          any[HeaderCarrier](),
          any[ExecutionContext]()
        )
    )
      .thenReturn(returnValue)

  "RelationshipController" should {

    "return Status: OK when successfully finding a relationship" in {

      when(mockMongoService.findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active)))
        .thenReturn(Future.successful(List(validTestRelationship)))

      val response = controller.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: NOT_FOUND for not finding data" in {

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List()))

      when(
        mockMongoService
          .findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))
      )
        .thenReturn(Future.successful(List()))

      val response = controller.findRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: CREATED for creating new record as a client" in {

      authStub(clientAffinityAndEnrolments)

      when(
        mockAuditService
          .sendCreateRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      when(mockMongoService.createRelationship(any[Relationship]()))
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List()))

      when(
        mockMongoService
          .findClientRelationships(eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List()))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(1))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: CREATED for creating new record as an agent" in {

      authStub(agentAffinityAndEnrolmentsCreds)

      when(
        mockAuditService
          .sendCreateRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .createRelationship(any[Relationship]())
      )
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List()))

      when(
        mockMongoService
          .findClientRelationships(eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List()))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED

      verify(mockMongoService, times(1))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: CREATED for creating new record as an agent and terminate similar relationship with any other agent" in {

      authStub(agentAffinityAndEnrolmentsCreds)
      // implicit val taxIdentifier = Some(Arn(""))

      when(
        mockAuditService
          .sendCreateRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .createRelationship(any[Relationship]())
      )
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List()))

      when(
        mockMongoService
          .findClientRelationships(eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(
          Future.successful(
            List(Relationship(Arn(validTestArn2), testService, validTestNINO, Some(Active), LocalDateTime.now(), None))
          )
        )

      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn2), eqs(testService), eqs(validTestNINO))
      )
        .thenReturn(Future.successful(true))

      when(
        mockAcaService
          .setRelationshipEnded(eqs(Arn(validTestArn2)), eqs(validTestNINO))(
            any[Option[Arn]](),
            any[HeaderCarrier](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(true))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED

      verify(mockMongoService, times(1))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
      verify(mockMongoService, times(1))
        .terminateRelationship(eqs(validTestArn2), eqs(testService), eqs(validTestNINO))
    }

    "send an audit event if the relationship is successfully created" in {

      authStub(agentAffinityAndEnrolmentsCreds)

      when(
        mockAuditService
          .sendCreateRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .createRelationship(eqs(validTestRelationship))
      )
        .thenReturn(Future.successful(()))

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List()))

      when(
        mockMongoService
          .findClientRelationships(eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List()))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)
      await(response)

      verify(mockAuditService, times(1))
        .sendCreateRelationshipEvent(any[AuditData]())(
          any[HeaderCarrier](),
          any[Request[Any]](),
          any[ExecutionContext]()
        )

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record, as a client" in {

      authStub(clientAffinityAndEnrolments)

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List(validTestRelationship)))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED

      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: CREATED when creating a new relationship that already exists, but do not add a duplicate record, as an agent" in {

      authStub(agentAffinityAndEnrolmentsCreds)

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List(validTestRelationship)))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED

      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: CREATED when additional relationships are created as a client" in {
      authStub(clientAffinityAndEnrolments)

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List(validTestRelationship)))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: CREATED when additional relationships are created as an agent" in {
      authStub(agentAffinityAndEnrolmentsCreds)

      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(Active))
      )
        .thenReturn(Future.successful(List(validTestRelationship)))

      val response =
        controller.createRelationship(validTestArn, testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe CREATED
      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: FORBIDDEN when logged in Agent ARN does not match given ARN when creating relationship" in {
      authStub(agentAffinityAndEnrolmentsCreds)

      val response =
        controller.createRelationship("JARN0000001", testService, validTestNINO)(fakeCreateRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(0))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: FORBIDDEN when logged in Client NINO does not match given NINO when creating relationship" in {
      authStub(clientAffinityAndEnrolments)

      val response =
        controller.createRelationship(validTestArn, testService, "AB123456C")(fakeCreateRequest)

      status(response) shouldBe FORBIDDEN

      verify(mockMongoService, times(0))
        .createRelationship(any[Relationship]())

      verify(mockMongoService, times(0))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: OK for deleting a record as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))
      )
        .thenReturn(Future.successful(true))

      when(
        mockAuditService
          .sendTerminatedRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())
    }

    "return Status: OK for deleting a record as an agent" in {
      authStub(agentAffinityAndEnrolmentsCreds)
      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))
      )
        .thenReturn(Future.successful(true))

      when(
        mockAuditService.sendTerminatedRelationshipEvent(any[AuditData]())(
          any[HeaderCarrier](),
          any[Request[Any]](),
          any[ExecutionContext]()
        )
      )
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())
    }

    "return Status: OK for deleting a record as hmrc" in {

      when(mockAppConfig.oldStrideRole).thenReturn("maintain agent relationships")
      when(mockAppConfig.newStrideRole).thenReturn("maintain_agent_relationships")
      authStub(strideEnrolmentsCred(oldStrideEnrolment))
      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))
      )
        .thenReturn(Future.successful(true))

      when(
        mockAuditService
          .sendHmrcLedDeleteRelationshipAuditEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())
    }

    "send an audit event if the relationship is successfully deleting" in {
      authStub(agentAffinityAndEnrolmentsCreds)
      when(
        mockMongoService
          .terminateRelationship(eqs(validTestArn), eqs(testService), eqs(validTestNINO))
      )
        .thenReturn(Future.successful(true))

      when(
        mockAuditService
          .sendTerminatedRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)
      await(response)
      verify(mockAuditService, times(1))
        .sendTerminatedRelationshipEvent(any[AuditData]())(
          any[HeaderCarrier](),
          any[Request[Any]](),
          any[ExecutionContext]()
        )
    }

    "return Status: NOT_FOUND for failing to delete a record as a client" in {
      authStub(clientAffinityAndEnrolments)
      when(
        mockMongoService
          .terminateRelationship(any[String](), any[String](), any[String]())
      )
        .thenReturn(Future.successful(false))

      when(
        mockAuditService
          .sendTerminatedRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())
    }

    "return Status: NOT_FOUND for failing to delete a record as an agent" in {
      authStub(agentAffinityAndEnrolmentsCreds)
      when(
        mockMongoService
          .terminateRelationship(any[String](), any[String](), any[String]())
      )
        .thenReturn(Future.successful(false))

      when(
        mockAuditService
          .sendTerminatedRelationshipEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())
    }

    "return Status: NOT_FOUND for failing to delete a record as hmrc" in {
      authStub(strideEnrolmentsCred(oldStrideEnrolment))
      when(
        mockMongoService
          .terminateRelationship(any[String](), any[String](), any[String]())
      )
        .thenReturn(Future.successful(false))

      when(
        mockAuditService
          .sendHmrcLedDeleteRelationshipAuditEvent(any[AuditData]())(
            any[HeaderCarrier](),
            any[Request[Any]](),
            any[ExecutionContext]()
          )
      )
        .thenReturn(Future.successful(()))

      val response =
        controller.terminateRelationship(validTestArn, testService, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .terminateRelationship(any[String](), any[String](), any[String]())
    }

    "return Status: FORBIDDEN when logged in Agent ARN does not match given ARN when deleting relationship" in {
      authStub(agentAffinityAndEnrolmentsCreds)

      val response =
        controller.terminateRelationship("JARN0000001", testService, validTestNINO)(fakeRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0))
        .terminateRelationship(any[String](), any[String](), any[String]())
    }

    "return Status: FORBIDDEN when logged in Client NINO does not match given NINO when deleting relationship" in {
      authStub(clientAffinityAndEnrolments)

      val response = controller.terminateRelationship(validTestArn, testService, "AB123456C")(fakeRequest)

      status(response) shouldBe FORBIDDEN
      verify(mockMongoService, times(0))
        .terminateRelationship(any[String](), any[String](), any[String]())
    }

    "return Status: OK for finding data via access control endpoint" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))
      )
        .thenReturn(Future.successful(List(validTestRelationship)))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: NOT_FOUND for not finding data via access control endpoint" in {
      when(
        mockMongoService
          .findRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO), eqs(RelationshipStatus.Active))
      )
        .thenReturn(Future.successful(List()))
      when(
        mockMongoService
          .findAnyRelationships(eqs(validTestArn), eqs(testService), eqs(validTestNINO))
      )
        .thenReturn(Future.successful(List()))

      val response =
        controller.findAfiRelationship(validTestArn, validTestNINO)(fakeRequest)

      status(response) shouldBe NOT_FOUND
      verify(mockMongoService, times(1))
        .findRelationships(any[String](), any[String](), any[String](), any[RelationshipStatus]())
    }

    "return Status: OK with json body of all agent invitations with TERMINATED status" in {

      val agentAction: AfiAction = { arn => credentials =>
        Future.successful(Ok)
      }

      authStub(agentAffinityAndEnrolmentsCreds)

      when(mockAgentClientAuthConnector.authorisedForAfi(strideRoles)(agentAction))
        .thenReturn(Future.successful(Ok))

      when(
        mockMongoService
          .findInactiveAgentRelationships(eqs(validTestArn))
      )
        .thenReturn(Future.successful(List(validTestRelationshipTerminated, validTestRelationshipCesa)))

      val response = controller.findInactiveRelationships(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findInactiveAgentRelationships(any[String]())
    }

    "return Status: OK with json body of all agent invitations with ACTIVE status" in {

      val agentAction: AfiAction = { arn => credentials =>
        Future.successful(Ok)
      }

      authStub(agentAffinityAndEnrolmentsCreds)

      when(mockAgentClientAuthConnector.authorisedForAfi(strideRoles)(agentAction))
        .thenReturn(Future.successful(Ok))

      when(
        mockMongoService
          .findActiveAgentRelationships(eqs(validTestArn))
      )
        .thenReturn(Future.successful(List(validTestRelationshipTerminated, validTestRelationshipCesa)))

      val response = controller.findActiveRelationships(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findActiveAgentRelationships(any[String]())
    }

    "return Status: OK with json body of all client invitations with TERMINATED status" in {

      val action: AfiAction = { arn => credentials =>
        Future.successful(Ok)
      }

      authStub(clientAffinityAndEnrolments)

      when(mockAgentClientAuthConnector.authorisedForAfi(strideRoles)(action))
        .thenReturn(Future.successful(Ok))

      when(
        mockMongoService
          .findInactiveClientRelationships(eqs(validTestNINO))
      )
        .thenReturn(Future.successful(List(validTestRelationshipTerminated, validTestRelationshipCesa)))

      val response = controller.findInactiveRelationships(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findInactiveClientRelationships(any[String]())
    }

    "return Status: OK with json body of all client invitations with ACTIVE status" in {

      val action: AfiAction = { arn => credentials =>
        Future.successful(Ok)
      }

      authStub(clientAffinityAndEnrolments)

      when(mockAgentClientAuthConnector.authorisedForAfi(strideRoles)(action))
        .thenReturn(Future.successful(Ok))

      when(
        mockMongoService
          .findActiveClientRelationships(eqs(validTestNINO))
      )
        .thenReturn(Future.successful(List(validTestRelationshipTerminated, validTestRelationshipCesa)))

      val response = controller.findActiveRelationships(fakeRequest)

      status(response) shouldBe OK
      verify(mockMongoService, times(1))
        .findActiveClientRelationships(any[String]())
    }

  }
}
