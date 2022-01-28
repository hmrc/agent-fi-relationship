/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.agentfirelationship.controllers.binders

import uk.gov.hmrc.agentfirelationship.binders.PathBinders
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.agentfirelationship.support.UnitSpec

class PathBindersSpec extends UnitSpec {

  "UtrBinder" should {
    val utrBinder = PathBinders.UtrBinder
    "bind a UTR" in {
      utrBinder.bind("value", "1307171695") shouldBe Right(Utr("1307171695"))
    }
    "unbind a Utr" in {
      utrBinder.unbind("value", Utr("1307171695")) shouldBe "1307171695"
    }
    "not bind when passed invalid utr" in {
      utrBinder.bind("value", "1234567890") shouldBe Left("Cannot parse parameter 'value' with value '1234567890' as 'Utr'")
    }
  }

}
