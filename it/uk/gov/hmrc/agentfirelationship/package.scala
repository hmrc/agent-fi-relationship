package uk.gov.hmrc

import java.time.LocalDateTime

package object agentfirelationship {
  val fakeCredId = "fakeCredId"
  val agentId = "TARN0000001"
  val agentId2 = "TARN0000002"
  val clientId = "AE123456C"
  val clientId2 = "AB123456A"
  val service = "afi"
  val auditDetails = Map("authProviderId" -> fakeCredId, "arn" -> agentId, "service" -> "personal-income-record", "clientId" -> clientId)
  val auditDetails2 = Map("authProviderId" -> fakeCredId, "arn" -> agentId, "service" -> "personal-income-record", "clientId" -> clientId2)
  val testResponseDate = LocalDateTime.now
}
