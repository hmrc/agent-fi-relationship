/*
 * Copyright 2024 HM Revenue & Customs
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

import java.time.LocalDateTime

package object agentfirelationship {
  val fakeCredId = "fakeCredId"
  val agentId    = "TARN0000001"
  val agentId2   = "TARN0000002"
  val clientId   = "AE123456C"
  val clientId2  = "AB123456A"
  val service    = "afi"
  val auditDetails =
    Map("authProviderId" -> fakeCredId, "arn" -> agentId, "service" -> "personal-income-record", "clientId" -> clientId)
  val auditDetails2 = Map(
    "authProviderId" -> fakeCredId,
    "arn"            -> agentId,
    "service"        -> "personal-income-record",
    "clientId"       -> clientId2
  )
  val testResponseDate = LocalDateTime.now
  val clientUtr        = "1307171695"
}
