import sbt.*

import play.sbt.PlayImport.ws

object AppDependencies {

  private val bootstrapVer: String  = "10.8.0"
  private val mongoVer: String      = "2.14.0"
  private val playVer: String       = "play-30"
  private val domainVer: String     = "13.0.0"

  lazy val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% s"bootstrap-backend-$playVer" % bootstrapVer,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVer"        % mongoVer,
    "uk.gov.hmrc"       %% s"domain-$playVer"            % domainVer
  )

  lazy val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVer"  % bootstrapVer,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVer" % mongoVer,
    "org.scalacheck"    %% "scalacheck"                % "1.19.0",
    "uk.gov.hmrc"       %% s"domain-test-$playVer"     % domainVer
  ).map(_ % Test)

}
