ThisBuild / scalaVersion     := "2.13.2"
ThisBuild / sbtVersion       := "1.3.13"

lazy val root = (project in file("."))
  .aggregate(thebutlerdidit.js, thebutlerdidit.jvm)
  .settings(
    publish := {},
    publishLocal := {},
  )

lazy val thebutlerdidit = crossProject(JSPlatform, JVMPlatform).in(file(".")).
  settings(
    name := "TheButlerDidIt",
    version := "0.1.0-SNAPSHOT",
    organization := "org.pfcoperez",
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.1.1" % Test,
    libraryDependencies += "com.lihaoyi" %%% "fastparse" % "2.2.4",
  ).
  jvmSettings(
    // Add JVM-specific settings here
  ).
  jsSettings(
    // Add JS-specific settings here
    scalaJSUseMainModuleInitializer := true,
  )
