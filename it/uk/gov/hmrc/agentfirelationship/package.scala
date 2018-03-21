package uk.gov.hmrc

import java.time.LocalDateTime

package object agentfirelationship {
  val fakeCredId = "fakeCredId"
  val agentId = "TARN0000001"
  val agentId2 = "TARN0000002"
  val clientId = "AE123456C"
  val service = "afi"
  val auditDetails = Map("authProviderId" -> fakeCredId, "arn" -> agentId, "service" -> "personal-income-record", "clientId" -> clientId)
  val testResponseDate = LocalDateTime.now
}
