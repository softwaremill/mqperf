import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings

val tapirVersion = "1.1.1"

lazy val commonSettings = commonSmlBuildSettings ++ Seq(
  organization := "com.softwaremill.mqperf",
  scalaVersion := "2.13.8"
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.13" % Test

//

lazy val dockerSettings = Seq(
  dockerExposedPorts := Seq(8080),
  dockerBaseImage := "eclipse-temurin:11.0.16.1_1-jre-jammy",
  dockerUsername := Some("softwaremill"),
  dockerUpdateLatest := true,
  Docker / version := git.gitHeadCommit.value.map(head => head.take(8) + "-" + (System.currentTimeMillis() / 1000)).getOrElse("latest")
)

//

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "root")
  .aggregate(core, kafka)

lazy val core: Project = (project in file("clients/core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "ch.qos.logback" % "logback-classic" % "1.4.1",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "org.typelevel" %% "cats-effect" % "3.4.4",
      "org.typelevel" %% "cats-core" % "2.9.0",
      scalaTest
    )
  )

lazy val kafka: Project = (project in file("clients/kafka"))
  .settings(commonSettings: _*)
  .settings(dockerSettings)
  .settings(Docker / packageName := "mqperf-kafka")
  .settings(
    name := "kafka",
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "3.2.3",
      scalaTest
    )
  )
  .dependsOn(core)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(DockerPlugin)
