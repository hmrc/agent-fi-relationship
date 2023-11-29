resolvers += "HMRC-open-artefacts-maven" at "https://open.artefacts.tax.service.gov.uk/maven2"
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)


addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.20")
addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "3.15.0")
addSbtPlugin("uk.gov.hmrc" % "sbt-distributables" % "2.2.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.6")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.16")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")  // provides sbt command "dependencyUpdates"
addDependencyTreePlugin

//fix for scoverage compile errors for scala 2.13.10
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always