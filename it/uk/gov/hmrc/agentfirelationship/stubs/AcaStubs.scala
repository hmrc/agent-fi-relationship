package uk.gov.hmrc.agentfirelationship.stubs

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import com.github.tomakehurst.wiremock.client.WireMock._

trait AcaStubs {

  def givenSetRelationshipEndedReturns(arn: Arn, nino: Nino, endedBy: String, status: Int) =
    stubFor(put(urlEqualTo(s"/agent-client-authorisation/invitations/set-relationship-ended"))
      .withRequestBody(equalToJson(
        s"""{
           |"arn": "${arn.value}",
           |"clientId": "${nino.value}",
           |"service": "PERSONAL-INCOME-RECORD",
           |"endedBy": "$endedBy"
           |}""".stripMargin))
      .willReturn(aResponse()
        .withStatus(status))
    )

}
