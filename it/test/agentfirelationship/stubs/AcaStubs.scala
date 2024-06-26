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

package agentfirelationship.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino

trait AcaStubs {

  def givenSetRelationshipEndedReturns(arn: Arn, nino: Nino, endedBy: String, status: Int): StubMapping =
    stubFor(
      put(urlEqualTo(s"/agent-client-authorisation/invitations/set-relationship-ended"))
        .withRequestBody(equalToJson(s"""{
                                        |"arn": "${arn.value}",
                                        |"clientId": "${nino.value}",
                                        |"service": "PERSONAL-INCOME-RECORD",
                                        |"endedBy": "$endedBy"
                                        |}""".stripMargin))
        .willReturn(
          aResponse()
            .withStatus(status)
        )
    )

}
