package uk.gov.hmrc.agentfirelationship.support

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.domain.SaAgentReference

trait WiremockAware {
  def wiremockBaseUrl: String
}

trait BasicUserAuthStubs { WiremockAware =>

  def isNotLoggedIn = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(401)
      .withHeader("WWW-Authenticate", s"""MDTP detail="MissingBearerToken"""")))
    this
  }
}

trait ClientUserAuthStubs extends BasicUserAuthStubs { WiremockAware =>

  def nino: String

  def isLoggedInAsClient = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "affinityGroup": "Individual",
         |  "allEnrolments": [
         |    {
         |      "key": "HMRC-NI",
         |      "identifiers": [
         |        {
         |          "key": "NINO",
         |          "value": "$nino"
         |        }
         |      ],
         |      "state": "Activated"
         |    }
         |  ]
         |}
       """.stripMargin)))

    this
  }
}

trait AgentAuthStubs extends BasicUserAuthStubs { WiremockAware =>

  def arn: String

  protected var saAgentReference: Option[SaAgentReference] = None

  def isLoggedInAndIsSubscribedAsAgent = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "affinityGroup": "Agent",
         |  "allEnrolments": [
         |    {
         |      "key": "HMRC-AS-AGENT",
         |      "identifiers": [
         |        {
         |          "key": "AgentReferenceNumber",
         |          "value": "$arn"
         |        }
         |      ],
         |      "state": "Activated"
         |    },
         |    {
         |      "key": "IR-PAYE-AGENT",
         |      "identifiers": [
         |        {
         |          "key": "IrAgentReference",
         |          "value": "HZ1234"
         |        }
         |      ],
         |      "state": "Activated"
         |    },
         |    {
         |      "key": "HMRC-AS-AGENT",
         |      "identifiers": [
         |        {
         |          "key": "AnotherIdentifier",
         |          "value": "not the ARN"
         |        },
         |        {
         |          "key": "AgentReferenceNumber",
         |          "value": "$arn"
         |        }
         |      ],
         |      "state": "Activated"
         |    }
         |  ]
         |}
       """.stripMargin)))
    this
  }

  def isLoggedInAndNotSubscribed = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "affinityGroup": "Agent",
         |  "allEnrolments": [
         |    {
         |      "key": "HMRC-AGENT-AGENT",
         |      "identifiers": [
         |        {
         |          "key": "AgentRefNumber",
         |          "value": "JARN1234567"
         |        }
         |      ],
         |      "state": "Activated"
         |    },
         |    {
         |      "key": "IR-PAYE-AGENT",
         |      "identifiers": [
         |        {
         |          "key": "IrAgentReference",
         |          "value": "HZ1234"
         |        }
         |      ],
         |      "state": "Activated"
         |    }
         |  ]
         |}
       """.stripMargin)))
    this
  }

  def isLoggedInWithoutAffinityGroup = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "allEnrolments": [
         |    {
         |      "key": "HMRC-NI",
         |      "identifiers": [
         |        {
         |          "key": "NINO",
         |          "value": "AB123456W"
         |        }
         |      ],
         |      "state": "Activated"
         |    }
         |  ]
         |}
       """.stripMargin)))

    this
  }

  def isLoggedInWithInvalidEnrolments = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "affinityGroup": "Agent",
         |  "allEnrolments": [
         |    {
         |      "key": "SOME-KEY",
         |      "identifiers": [
         |        {
         |          "key": "SOME-IDENTIFIER",
         |          "value": "INVALID"
         |        }
         |      ],
         |      "state": "Activated"
         |    }
         |  ]
         |}
       """.stripMargin)))

    this
  }

}