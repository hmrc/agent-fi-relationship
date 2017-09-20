package uk.gov.hmrc

import java.time.LocalDate

package object agentfirelationship {
  val fakeCredId = "fakeCredId"
  val agentId = "Agent123"
  val clientId = "Client123"
  val service = "afi"
  val auditDetails = Map("authProviderId" -> fakeCredId, "arn" -> agentId, "regime" -> "afi", "regimeId" -> clientId)
  val testResponseDate = LocalDate.now.toString
}
