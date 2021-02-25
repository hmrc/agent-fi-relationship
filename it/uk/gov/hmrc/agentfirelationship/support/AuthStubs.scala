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
         |  ],
         |  "optionalCredentials": {
         |    "providerId": "12345-credId",
         |    "providerType": "GovernmentGateway"
         |  }
         |}
       """.stripMargin)))

    this
  }
}

trait AgentAuthStubs extends BasicUserAuthStubs { WiremockAware =>

  def arn: String

  protected var saAgentReference: Option[SaAgentReference] = None

  def isLoggedInAndIsSubscribedAsAgent: AgentAuthStubs = isLoggedInAndIsSubscribedAsAgent(arn)

  def isLoggedInAndIsSubscribedAsAgent(newArn: String): AgentAuthStubs = {
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
         |          "value": "$newArn"
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
         |          "value": "$newArn"
         |        }
         |      ],
         |      "state": "Activated"
         |    }
         |  ],
         |  "optionalCredentials": {
         |    "providerId": "12345-credId",
         |    "providerType": "GovernmentGateway"
         |  }
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
         |  ],
         |  "optionalCredentials": {
         |    "providerId": "12345-credId",
         |    "providerType": "GovernmentGateway"
         |  }
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
         |  ],
         |  "optionalCredentials": {
         |    "providerId": "12345-credId",
         |    "providerType": "GovernmentGateway"
         |  }
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
         |  ],
         |  "optionalCredentials": {
         |    "providerId": "12345-credId",
         |    "providerType": "GovernmentGateway"
         |  }
         |}
       """.stripMargin)))

    this
  }

  def isLoggedInWithStride(key: String) = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "allEnrolments": [
         |    {
         |      "key": "$key"
         |    }
         |  ],
         |  "optionalCredentials": {
         |    "providerId": "12345-credId",
         |    "providerType": "PrivilegedApplication"
         |  }
         |}
       """.stripMargin)))

    this
  }

}