/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.models

import java.time.LocalDateTime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

class RelationshipSpec extends AnyFlatSpec with Matchers {

  private val relationship = Relationship(
    arn = Arn("TARN0000001"),
    service = "afi",
    clientId = "AE123456",
    startDate = LocalDateTime.parse("2026-01-01T12:00:00"),
    endDate = None
  )

  it should "serialize ACTIVE status using the persisted wire value" in {
    val json = Json.toJson(relationship.copy(relationshipStatus = Some(RelationshipStatus.Active)))

    (json \ "relationshipStatus").as[String] shouldBe "ACTIVE"
  }

  it should "serialize and deserialize TERMINATED status using the persisted wire value" in {
    val terminated = relationship.copy(relationshipStatus = Some(RelationshipStatus.Terminated))
    val json       = Json.toJson(terminated)

    (json \ "relationshipStatus").as[String] shouldBe "TERMINATED"
    json.as[Relationship] shouldBe terminated
  }
}
