import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "2.13.12"

lazy val root = (project in file("."))
  .settings(
    name                     := "agent-fi-relationship",
    organization             := "uk.gov.hmrc",
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
    Test / scalafmtOnCompile    := true,
    Test / logBuffered          := false
  )
  .settings(
    CodeCoverageSettings.scoverageSettings,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources",
    Test / parallelExecution := true
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.test)
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile    := true,
    Test / logBuffered          := false,
    Test / parallelExecution    := false
  )
