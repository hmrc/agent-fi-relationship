package uk.gov.hmrc.agentfirelationship.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

trait DesStubs {

  val someAlienAgent = """{"hasAgent":false,"agentId":"alien"}"""
  val someCeasedAgent = """{"hasAgent":true,"agentId":"ex-agent","agentCeasedDate":"someDate"}"""

  def givenClientHasRelationshipWithAgentInCESA(taxIdentifier: TaxIdentifier, agentId: String): StubMapping = {
    val url = desUrlForTaxIdentifier(taxIdentifier)
    stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withStatus(200)
          .withBody(s"""{"agents":[$someCeasedAgent,{"hasAgent":true,"agentId":"$agentId"}, $someAlienAgent]}""")))
  }

  def givenClientHasRelationshipWithMultipleAgentsInCESA(taxIdentifier: TaxIdentifier, agentIds: Seq[String]): StubMapping = {
    val url = desUrlForTaxIdentifier(taxIdentifier)
    stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withStatus(200)
          .withBody(s"""{"agents":[${agentIds.map(id => s"""{"hasAgent":true,"agentId":"$id"}""").mkString(",")}, $someAlienAgent, $someCeasedAgent ]}""")))
  }

  def givenClientRelationshipWithAgentCeasedInCESA(taxIdentifier: TaxIdentifier, agentId: String): StubMapping = {
    val url = desUrlForTaxIdentifier(taxIdentifier)
    stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withStatus(200)
          .withBody(s"""{"agents":[{"hasAgent":true,"agentId":"$agentId","agentCeasedDate":"2010-01-01"}]}""")))
  }

  def givenAllClientRelationshipsWithAgentsCeasedInCESA(taxIdentifier: TaxIdentifier, agentIds: Seq[String]): StubMapping = {
    val url = desUrlForTaxIdentifier(taxIdentifier)
    stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withStatus(200)
          .withBody(s"""{"agents":[${agentIds.map(id => s"""{"hasAgent":true,"agentId":"$id","agentCeasedDate":"2020-12-31"}""").mkString(",")}]}""")))
  }

  def givenClientHasNoActiveRelationshipWithAgentInCESA(taxIdentifier: TaxIdentifier): StubMapping = {
    val url = desUrlForTaxIdentifier(taxIdentifier)
    stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withStatus(200)
          .withBody(s"""{"agents":[$someCeasedAgent, $someAlienAgent]}""")))
  }

  def givenClientHasNoRelationshipWithAnyAgentInCESA(taxIdentifier: TaxIdentifier): StubMapping = {
    val url = desUrlForTaxIdentifier(taxIdentifier)
    stubFor(
      get(urlEqualTo(url))
        .willReturn(aResponse().withStatus(200)
          .withBody(s"""{}""")))
  }

  def givenClientHasNoNinoInCESA(nino: Nino): StubMapping = {
    stubFor(
      get(urlEqualTo(s"/registration/relationship/nino/${nino.value}"))
        .willReturn(aResponse().withStatus(404)
          .withBody(
            s"""{
               |"code": "NOT_FOUND_NINO",
               |"reason": "The remote endpoint has indicated that the NINO cannot be found."
               |}""".stripMargin)))
  }

  def givenClientIsUnknownInCESAFor(nino: Nino): StubMapping = {
    stubFor(
      get(urlEqualTo(s"/registration/relationship/nino/${nino.value}"))
        .willReturn(aResponse().withStatus(404)))
  }

  def givenDesReturnsServerError(): StubMapping = {
    stubFor(
      get(urlMatching(s"/registration/.*"))
        .willReturn(aResponse().withStatus(500)))
  }

  def givenDesReturnsServiceUnavailable(): StubMapping = {
    stubFor(
      get(urlMatching(s"/registration/.*"))
        .willReturn(aResponse().withStatus(503)))
  }

  private val desUrlForTaxIdentifier: TaxIdentifier => String = {
    case Utr(utr) => s"/registration/relationship/utr/$utr"
    case Nino(nino) => s"/registration/relationship/nino/$nino"
    case e => throw new RuntimeException(s"$e Not supported tax identifier")
  }
}
