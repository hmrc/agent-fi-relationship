
lazy val root = (project in file("."))
  .settings(
    name := "agent-fi-relationship",
    organization := "uk.gov.hmrc",
    scalaVersion := "2.13.10",
    majorVersion := 1,
    PlayKeys.playDefaultPort := 9427,
    resolvers ++= Seq(
      Resolver.typesafeRepo("releases"),
    ),
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-Xlint:-missing-interpolator,_",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-Wconf:src=routes/.*:s", // silence warnings from routes
    ),
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always),
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

//TODO integration test settings as sub project?
//lazy val integrationTest = (project in file("it"))
//  .settings(
//    Keys.fork := true,
//    unmanagedSourceDirectories += baseDirectory(_ / "it").value,
//    parallelExecution := false
//  )

