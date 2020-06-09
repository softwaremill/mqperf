import sbt._
import sbt.Keys._

lazy val commonSettings = Seq(
  version := "3.0",
  scalaVersion := "2.12.11"
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    // This way we kill 'zombie' senders and receivers after unsuccessful test(s)
    fork in Test := true
  )

/*
lazy val oracleaq = project.in(file("oracleaq")).
  dependsOn(root).
  settings(commonSettings: _*).
  settings(name := "mqperfext").
  settings(libraryDependencies ++= Seq(
    //    "com.oracle" % "aqapi_2.11" % "1.0.0",
    //    "com.oracle" % "ojdbc6_2.11" % "1.0.0",
    "javax.transaction" % "jta" % "1.1"
  )
  )
 */

name := "mqperf"

val prometheusVersion = "0.9.0"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.11.797" exclude ("commons-logging", "commons-logging"),
  "org.json4s" %% "json4s-native" % "3.6.8",
  "org.mongodb" % "mongodb-driver" % "3.12.5",
  "com.rabbitmq" % "amqp-client" % "5.9.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.30",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.30",
  "org.apache.kafka" % "kafka-clients" % "2.5.0",
  "org.scalatest" %% "scalatest" % "3.1.2" % Test,
  "com.geteventstore" %% "eventstore-client" % "7.1.0",
  "org.apache.activemq" % "activemq-client" % "5.15.12" exclude ("org.apache.geronimo.specs", "geronimo-jms_1.1_spec"),
  "com.typesafe" % "config" % "1.4.0",
  "org.apache.activemq" % "artemis-jms-client" % "2.13.0" exclude ("commons-logging", "commons-logging"),
  "org.apache.rocketmq" % "rocketmq-client" % "4.7.0" exclude ("io.netty", "netty-all") exclude ("commons-logging", "commons-logging"),
  "com.softwaremill.kmq" %% "core" % "0.2.3",
  "io.prometheus" % "simpleclient" % prometheusVersion,
  "io.prometheus" % "simpleclient_common" % prometheusVersion
)

assemblyOption in assembly ~= {
  _.copy(includeBin = true, includeScala = false, includeDependency = false)
}

assemblyMergeStrategy in assembly := {
  case PathList(ps @ _*) if ps.last == "io.netty.versions.properties" => MergeStrategy.first
  case x                                                              => (assemblyMergeStrategy in assembly).value(x)
}
