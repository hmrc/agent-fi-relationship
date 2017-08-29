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

package uk.gov.hmrc.agentfirelationship.audit

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

import com.google.inject.Singleton
import play.api.mvc.Request
import uk.gov.hmrc.agentfirelationship.audit.AgentClientRelationshipEvent.AgentClientRelationshipEvent
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.collection.JavaConversions
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future
import scala.util.Try

object AgentClientRelationshipEvent extends Enumeration {
  val AgentClientRelationshipCreated, AgentClientRelationshipEnded = Value
  type AgentClientRelationshipEvent = Value
}

class AuditData {

  private val details = new ConcurrentHashMap[String, Any]

  def set(key: String, value: Any): Unit = {
    details.put(key, value)
  }

  def getDetails: Map[String, Any] = {
    JavaConversions.mapAsScalaMap(details).toMap
  }
}

@Singleton
class AuditService @Inject()(val auditConnector: AuditConnector) {

  private def collectDetails(data: Map[String, Any], fields: Seq[String]): Seq[(String, Any)] = fields.map { f =>
    (f, data.getOrElse(f, ""))
  }

  val createRelationshipDetailsFields = Seq(
    "authProviderId",
    "arn",
    "regime",
    "regimeId"
  )

  val DeleteRelationshipFields = Seq(
    "authProviderId",
    "arn",
    "regime",
    "regimeId"
  )

  def sendCreateRelationshipEvent(audit: Future[AuditData])(implicit hc: HeaderCarrier, request: Request[Any]): Unit = {
    audit.map { auditData =>
      auditEvent(AgentClientRelationshipEvent.AgentClientRelationshipCreated, "agent fi create relationship",
        collectDetails(auditData.getDetails, createRelationshipDetailsFields))
    }
  }

  def sendDeleteRelationshipEvent(audit: Future[AuditData])(implicit hc: HeaderCarrier, request: Request[Any]): Unit = {
    audit.map { auditData =>
      auditEvent(AgentClientRelationshipEvent.AgentClientRelationshipEnded, "agent fi delete relationship",
        collectDetails(auditData.getDetails, DeleteRelationshipFields))
    }
  }

  private def auditEvent(event: AgentClientRelationshipEvent, transactionName: String, details: Seq[(String, Any)] = Seq.empty)
                               (implicit hc: HeaderCarrier, request: Request[Any]): Future[Unit] = {
    send(createEvent(event, transactionName, details: _*))
  }

  private def createEvent(event: AgentClientRelationshipEvent, transactionName: String, details: (String, Any)*)
                         (implicit hc: HeaderCarrier, request: Request[Any]): DataEvent = {

    def toString(x: Any): String = x match {
      case t: TaxIdentifier => t.value
      case _ => x.toString
    }

    val detail = hc.toAuditDetails(details.map(pair => pair._1 -> toString(pair._2)): _*)
    val tags = hc.toAuditTags(transactionName, request.path)
    DataEvent(auditSource = "agent-fi-relationship",
      auditType = event.toString,
      tags = tags,
      detail = detail
    )
  }

  private def send(events: DataEvent*)(implicit hc: HeaderCarrier): Future[Unit] = {
    Future {
      events.foreach { event =>
        Try(auditConnector.sendEvent(event))
      }
    }
  }
}