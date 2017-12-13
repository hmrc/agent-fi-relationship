package uk.gov.hmrc

import org.joda.time.DateTime

package object agentfirelationship {
  val fakeCredId = "fakeCredId"
  val agentId = "TARN0000001"
  val agentId2 = "TARN0000002"
  val clientId = "AE123456C"
  val service = "afi"
  val auditDetails = Map("authProviderId" -> fakeCredId, "arn" -> agentId, "regime" -> "afi", "regimeId" -> clientId)
  val testResponseDate = DateTime.now
}
