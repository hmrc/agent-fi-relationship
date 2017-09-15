/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.connectors

import java.net.URL
import javax.inject.{Inject, Named}

import com.google.inject.Singleton
import play.api.libs.json.{Format, JsValue, Json}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthConnector @Inject()(@Named("auth-baseUrl") baseUrl: URL, httpGet: HttpGet) {
  val authorityUrl = new URL(baseUrl, "/auth/authority")

  def userDetails(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UserDetails] = {
    for {
      authority <- httpGet.GET[JsValue](authorityUrl.toString)
      userDetails <- httpGet.GET[UserDetails](new URL(authorityUrl, (authority \ "userDetailsLink").as[String]).toString)
    } yield userDetails
  }
}

object UserDetails {
  implicit val format: Format[UserDetails] = Json.format[UserDetails]
}

case class UserDetails(authProviderId: String)
