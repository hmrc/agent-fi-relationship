
lazy val root = (project in file("."))
  .settings(
    name := "agent-fi-relationship",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.12.15",
    majorVersion := 1,
    PlayKeys.playDefaultPort := 9427,
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
    ),
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,_",
      "-Yno-adapted-args",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-Wconf:src=routes/.*:s", // silence warnings from routes
    ),
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    routesImport ++= Seq("uk.gov.hmrc.agentfirelationship.binders.PathBinders._"),
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )
  .settings(
    CodeCoverageSettings.scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / parallelExecution := false
  )
  .configs(IntegrationTest)
  .settings(
    IntegrationTest / Keys.fork := true,
    Defaults.itSettings,
    IntegrationTest / unmanagedSourceDirectories += baseDirectory(_ / "it").value,
    IntegrationTest / parallelExecution := false
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)

inConfig(IntegrationTest)(scalafmtCoreSettings)
