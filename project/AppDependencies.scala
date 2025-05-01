import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  private val mongoVer = "2.6.0"
  private val bootstrapVer = "9.11.0"

  lazy val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVer,
    "uk.gov.hmrc"       %% "agent-mtd-identifiers"     % "2.2.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % mongoVer
  )

  lazy val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"    % bootstrapVer % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30"   % mongoVer     % Test
  )

}
