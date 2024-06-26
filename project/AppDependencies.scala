import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  private val mongoVer = "2.1.0"
  private val bootstrapVer = "9.0.0"

  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVer,
    "uk.gov.hmrc"       %% "agent-mtd-identifiers"     % "1.15.0",
    "com.typesafe.play" %% "play-json"                 % "2.10.5",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoVer
  )

  lazy val test = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"    % bootstrapVer % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30"   % mongoVer     % Test
  )

}
