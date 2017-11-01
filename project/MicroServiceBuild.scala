import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "agent-fi-relationship"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.1.0",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.12.0",
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % "6.12.0",
    "uk.gov.hmrc" %% "auth-client" % "2.3.0",
    "uk.gov.hmrc" %% "domain" % "5.0.0",
    "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "2.4.0",
    "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.5.0"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "3.0.0" % scope,
    "org.scalatest" %% "scalatest" % "2.2.6" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.mockito" % "mockito-core" % "2.7.22" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
    "com.github.tomakehurst" % "wiremock" % "1.58" % scope
  )

}
