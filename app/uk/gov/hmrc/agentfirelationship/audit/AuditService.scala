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

package uk.gov.hmrc.agentfirelationship.audit

import java.util.concurrent.ConcurrentHashMap

import javax.inject.Inject
import com.google.inject.Singleton
import play.api.mvc.Request
import uk.gov.hmrc.agentfirelationship.audit.AgentClientRelationshipEvent.AgentClientRelationshipEvent
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.collection.JavaConversions
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import uk.gov.hmrc.http.HeaderCarrier

object AgentClientRelationshipEvent extends Enumeration {
  val AgentClientRelationshipCreated, ClientTerminatedAgentServiceAuthorisation,
  AgentClientRelationshipCreatedFromExisting, HmrcRemovedAgentServiceAuthorisation,
  TerminateMtdAgentForIndividualsRelationships = Value
  type AgentClientRelationshipEvent = Value
}

class AuditData {

  private val details = new ConcurrentHashMap[String, Any]

  def set(key: String, value: Any): Any = details.put(key, value)

  def getDetails: Map[String, Any] =
    JavaConversions.mapAsScalaMap(details).toMap
}

@Singleton
class AuditService @Inject()(val auditConnector: AuditConnector) {

  private def collectDetails(data: Map[String, Any], fields: Seq[String]): Seq[(String, Any)] =
    fields.map { f =>
      (f, data.getOrElse(f, ""))
    }

  val createRelationshipDetailsFields: Seq[String] =
    Seq("authProviderId", "arn", "service", "clientId", "clientIdType")

  val TerminateRelationshipFields: Seq[String] =
    Seq("authProviderId", "authProviderIdType", "agentReferenceNumber", "service", "clientId", "clientIdType")

  val createdFromExistingRelationship: Seq[String] =
    Seq("agentReferenceNumber", "saAgentRef", "service", "clientId", "clientIdType")

  val hmrcDeleteRelationshipDetailsFields: Seq[String] =
    Seq("authProviderId", "authProviderIdType", "agentReferenceNumber", "clientId", "service")

  def sendCreateRelationshipEvent(
    auditData: AuditData)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] =
    auditEvent(
      AgentClientRelationshipEvent.AgentClientRelationshipCreated,
      "agent fi create relationship",
      collectDetails(auditData.getDetails, createRelationshipDetailsFields)
    )

  def sendTerminatedRelationshipEvent(
    auditData: AuditData)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] =
    auditEvent(
      AgentClientRelationshipEvent.ClientTerminatedAgentServiceAuthorisation,
      "client terminated agent:service authorisation",
      collectDetails(auditData.getDetails, TerminateRelationshipFields)
    )

  def sendCreateRelationshipFromExisting(
    auditData: AuditData)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] =
    auditEvent(
      AgentClientRelationshipEvent.AgentClientRelationshipCreatedFromExisting,
      "Agent client relationship created from CESA",
      collectDetails(auditData.getDetails, createdFromExistingRelationship)
    )

  def sendHmrcLedDeleteRelationshipAuditEvent(auditData: AuditData)(
    implicit
    headerCarrier: HeaderCarrier,
    request: Request[Any],
    ec: ExecutionContext): Future[Unit] =
    auditEvent(
      AgentClientRelationshipEvent.HmrcRemovedAgentServiceAuthorisation,
      "hmrc remove agent:service authorisation",
      collectDetails(auditData.getDetails, hmrcDeleteRelationshipDetailsFields)
    )

  def sendTerminateMtdAgentForIndividualsRelationships(
    arn: Arn,
    status: String,
    credId: String,
    failureReason: Option[String] = None)(
    implicit hc: HeaderCarrier,
    request: Request[Any],
    ec: ExecutionContext): Future[Unit] = {

    val details = failureReason match {
      case Some(fr) =>
        Seq(
          "agentReferenceNumber" -> arn.value,
          "status"               -> status,
          "credId"               -> credId,
          "authProvider"         -> "PrivilegedApplication",
          "failureReason"        -> fr
        )
      case None =>
        Seq(
          "agentReferenceNumber" -> arn.value,
          "status"               -> status,
          "credId"               -> credId,
          "authProvider"         -> "PrivilegedApplication"
        )
    }

    auditEvent(
      AgentClientRelationshipEvent.TerminateMtdAgentForIndividualsRelationships,
      "terminate-mtd-agent-for-individuals-relationships",
      details
    )
  }

  private def auditEvent(
    event: AgentClientRelationshipEvent,
    transactionName: String,
    details: Seq[(String, Any)] = Seq.empty)(
    implicit hc: HeaderCarrier,
    request: Request[Any],
    ec: ExecutionContext): Future[Unit] =
    send(createEvent(event, transactionName, details: _*))

  private def createEvent(event: AgentClientRelationshipEvent, transactionName: String, details: (String, Any)*)(
    implicit hc: HeaderCarrier,
    request: Request[Any]): DataEvent = {

    def toString(x: Any): String = x match {
      case t: TaxIdentifier => t.value
      case _                => x.toString
    }

    val detail =
      hc.toAuditDetails(details.map(pair => pair._1 -> toString(pair._2)): _*)
    val tags = hc.toAuditTags(transactionName, request.path)
    DataEvent(auditSource = "agent-fi-relationship", auditType = event.toString, tags = tags, detail = detail)
  }

  private def send(events: DataEvent*)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future {
      events.foreach { event =>
        Try(auditConnector.sendEvent(event))
      }
    }
}
