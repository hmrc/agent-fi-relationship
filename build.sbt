import uk.gov.hmrc.DefaultBuildSettings
import CodeCoverageSettings.scoverageSettings

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "3.3.7"

val scalaCompilerOptions = Seq(
  "-Werror",
  "-feature",
  "-Wconf:src=target/.*:s",
  "-Wconf:src=routes/.*:s",
)

lazy val root = (project in file("."))
  .settings(
    name                     := "agent-fi-relationship",
    organization             := "uk.gov.hmrc",
    PlayKeys.playDefaultPort := 9427,
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
    ),
    scalacOptions ++= scalaCompilerOptions,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always),
    routesImport ++= Seq("uk.gov.hmrc.agentfirelationship.binders.PathBinders.given"),
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile    := true,
    Compile / scalacOptions := (Compile / scalacOptions).value.distinct,
    Test / scalacOptions    := (Test / scalacOptions).value.distinct,
    Test / logBuffered          := false
  )
  .settings(
    scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / parallelExecution := true
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.test)
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile    := true,
    Compile / scalacOptions ++= scalaCompilerOptions,
    Test / scalacOptions    ++= scalaCompilerOptions,
    Compile / scalacOptions := (Compile / scalacOptions).value.distinct,
    Test / scalacOptions    := (Test / scalacOptions).value.distinct,
    Test / logBuffered          := false,
    Test / parallelExecution    := false
  )
