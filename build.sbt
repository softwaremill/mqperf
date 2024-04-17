import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings

val tapirVersion = "1.10.4"

lazy val commonSettings = commonSmlBuildSettings ++ Seq(
  organization := "com.softwaremill.mqperf",
  scalaVersion := "3.4.1"
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18" % Test

//

lazy val dockerSettings = Seq(
  dockerExposedPorts := Seq(8080),
  dockerBaseImage := "eclipse-temurin:17.0.10_7-jre-jammy",
  dockerUsername := Some("softwaremill"),
  dockerUpdateLatest := true,
  Docker / version := git.gitHeadCommit.value.map(head => head.take(8) + "-" + (System.currentTimeMillis() / 1000)).getOrElse("latest")
)

//

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "root")
  .aggregate(core, kafka, rabbitmq, postgres)

lazy val core: Project = (project in file("clients/core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "ch.qos.logback" % "logback-classic" % "1.5.2",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % tapirVersion,
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
      "org.typelevel" %% "cats-effect" % "3.4.4",
      "org.typelevel" %% "cats-core" % "2.10.0",
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
      "org.apache.kafka" % "kafka-clients" % "3.7.0",
      scalaTest
    )
  )
  .dependsOn(core)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(DockerPlugin)

lazy val rabbitmq: Project = (project in file("clients/rabbitmq"))
  .settings(commonSettings: _*)
  .settings(dockerSettings)
  .settings(Docker / packageName := "mqperf-rabbitmq")
  .settings(
    name := "rabbitmq",
    libraryDependencies ++= Seq(
      "com.rabbitmq" % "amqp-client" % "5.21.0",
      scalaTest
    )
  )
  .dependsOn(core)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(DockerPlugin)

val doobieVersion = "1.0.0-RC2"

lazy val postgres: Project = (project in file("clients/postgres"))
  .settings(commonSettings: _*)
  .settings(dockerSettings)
  .settings(Docker / packageName := "mqperf-postgres")
  .settings(
    name := "postgres",
    libraryDependencies ++= Seq(
      "org.postgresql" % "r2dbc-postgresql" % "0.9.2.RELEASE",
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      scalaTest
    )
  )
  .dependsOn(core)
  .enablePlugins(JavaServerAppPackaging)
  .enablePlugins(DockerPlugin)
