import sbt.*

import play.sbt.PlayImport.ws

object AppDependencies {

  private val mongoVer     = "2.11.0"
  private val bootstrapVer = "10.5.0"

  lazy val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVer,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoVer,
    "uk.gov.hmrc"       %% "domain-play-30"            % "11.0.0"
  )

  lazy val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVer % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoVer     % Test,
    "org.scalacheck"    %% "scalacheck"              % "1.18.1"     % Test
  )

}
