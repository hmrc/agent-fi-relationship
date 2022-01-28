/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.controllers.services

import org.mockito.ArgumentMatchers.{eq => eqs}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._
import uk.gov.hmrc.agentfirelationship.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentfirelationship.connectors.{DesConnector, MappingConnector}
import uk.gov.hmrc.agentfirelationship.controllers.support.ResettingMockitoSugar
import uk.gov.hmrc.agentfirelationship.services.CesaRelationshipCopyService
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.{AgentCode, Generator, Nino, SaAgentReference}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentfirelationship.support.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class CesaRelationshipCopyServiceSpec extends UnitSpec with BeforeAndAfterEach with ResettingMockitoSugar {

  val testDataGenerator = new Generator()
  val arn = Arn("AARN0000002")
  val saAgentRef = SaAgentReference("T1113T")
  val saAgentRef2 = SaAgentReference("T1123T")
  val saAgentRef3 = SaAgentReference("T1133T")
  val mtdItId = MtdItId("ABCDEF123456789")
  val agentCode = AgentCode("ABC1234")
  val nino: Nino = testDataGenerator.nextNino

  private val des = resettingMock[DesConnector]
  private val mapping = resettingMock[MappingConnector]
  private val auditService = resettingMock[AuditService]

  val hc = HeaderCarrier()
  val ec: ExecutionContext = implicitly[ExecutionContext]

  "CesaRelationshipCopyService" should {

    val service = new CesaRelationshipCopyService(des, mapping, auditService)

    "return an existing relationship in Cesa and Agent Mapping if it exists" in {
      val auditData = new AuditData()

      commonMultipleRelationshipExists()

      val agentRefs: Set[SaAgentReference] =
        await(
          service
            .lookupCesaForOldRelationship(arn, nino)(ec, hc, auditData))

      agentRefs shouldBe Set(saAgentRef)
    }

    "return an existing relationship in Cesa and Agent Mapping if multiple exists" in {
      val auditData = new AuditData()

      commonMultipleRelationshipExists()

      val agentRefs: Set[SaAgentReference] =
        await(
          service
            .lookupCesaForOldRelationship(arn, nino)(ec, hc, auditData))

      agentRefs shouldBe Set(saAgentRef)
    }

    "return empty for an non-existing relationship in Cesa and Agent Mapping" in {
      val auditData = new AuditData()

      cesaAndMappingReturnEmptyRelationshipSets()

      val agentRefs: Set[SaAgentReference] =
        await(
          service
            .lookupCesaForOldRelationship(arn, nino)(ec, hc, auditData))

      agentRefs shouldBe Set.empty
    }

    "return empty for an non-existing relationship in Cesa and Agent Mapping with non-matching agentRefs" in {
      val auditData = new AuditData()

      commonRelationshipDoesNotExistInBothCesaAndMapping()

      val agentRefs: Set[SaAgentReference] =
        await(
          service
            .lookupCesaForOldRelationship(arn, nino)(ec, hc, auditData))

      agentRefs shouldBe Set.empty
    }

    "return empty for an non-existing relationship in Agent Mapping" in {
      val auditData = new AuditData()

      commonRelationshipDoesNotExistInMapping()

      val agentRefs: Set[SaAgentReference] =
        await(
          service
            .lookupCesaForOldRelationship(arn, nino)(ec, hc, auditData))

      agentRefs shouldBe Set.empty
    }

    "return empty for an non-existing relationship in Cesa" in {
      val auditData = new AuditData()

      commonRelationshipDoesNotExistInCesa()

      val agentRefs: Set[SaAgentReference] =
        await(
          service
            .lookupCesaForOldRelationship(arn, nino)(ec, hc, auditData))

      agentRefs shouldBe Set.empty
    }
  }

  private def commonMultipleRelationshipExists(): Unit = {
    when(des.getClientSaAgentSaReferences(eqs(nino))(eqs(hc), eqs(ec)))
      .thenReturn(Future successful Seq(saAgentRef, saAgentRef3))
    when(mapping.getSaAgentReferencesFor(eqs(arn))(eqs(hc)))
      .thenReturn(Future successful Seq(saAgentRef, saAgentRef2))
    ()
  }

  private def cesaAndMappingReturnEmptyRelationshipSets(): Unit = {
    when(des.getClientSaAgentSaReferences(eqs(nino))(eqs(hc), eqs(ec)))
      .thenReturn(Future successful Seq())
    when(mapping.getSaAgentReferencesFor(eqs(arn))(eqs(hc)))
      .thenReturn(Future successful Seq())
    ()
  }

  private def commonRelationshipDoesNotExistInBothCesaAndMapping(): Unit = {
    when(des.getClientSaAgentSaReferences(eqs(nino))(eqs(hc), eqs(ec)))
      .thenReturn(Future successful Seq(saAgentRef))
    when(mapping.getSaAgentReferencesFor(eqs(arn))(eqs(hc)))
      .thenReturn(Future successful Seq(saAgentRef2))
    ()
  }

  private def commonRelationshipDoesNotExistInMapping(): Unit = {
    when(des.getClientSaAgentSaReferences(eqs(nino))(eqs(hc), eqs(ec)))
      .thenReturn(Future successful Seq(saAgentRef))
    when(mapping.getSaAgentReferencesFor(eqs(arn))(eqs(hc)))
      .thenReturn(Future successful Seq())
    ()
  }

  private def commonRelationshipDoesNotExistInCesa(): Unit = {
    when(des.getClientSaAgentSaReferences(eqs(nino))(eqs(hc), eqs(ec)))
      .thenReturn(Future successful Seq())
    when(mapping.getSaAgentReferencesFor(eqs(arn))(eqs(hc)))
      .thenReturn(Future successful Seq(saAgentRef))
    ()
  }
}
