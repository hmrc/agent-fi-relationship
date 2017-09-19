package uk.gov.hmrc

package object agentfirelationship {
  val fakeCredId = "fakeCredId"
  val agentId = "Agent123"
  val clientId = "Client123"
  val service = "afi"
  val auditDetails = Map("authProviderId" -> fakeCredId, "arn" -> agentId, "regime" -> "afi", "regimeId" -> clientId)
  val validDateFormatInString = "2017-09-19T14:58:21.070"
}
