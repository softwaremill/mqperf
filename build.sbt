import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings

val tapirVersion = "1.1.1"

lazy val commonSettings = commonSmlBuildSettings ++ Seq(
  organization := "com.softwaremill.mqperf",
  scalaVersion := "2.13.8"
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.13" % Test

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
      scalaTest
    )
  )

lazy val kafka: Project = (project in file("clients/kafka"))
  .settings(commonSettings: _*)
  .settings(
    name := "kafka",
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "3.2.3",
      scalaTest
    )
  )
  .dependsOn(core)
