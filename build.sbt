import AssemblyKeys._

assemblySettings

name := "mqperf"

version := "1.0"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.7.12" exclude("commons-logging", "commons-logging"),
  "org.json4s" %% "json4s-native" % "3.2.9",
  "org.mongodb" % "mongo-java-driver" % "2.12.2",
  "com.rabbitmq" % "amqp-client" % "3.3.2",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
  "org.scalatest" %% "scalatest" % "2.1.3" % "test"
)

assemblyOption in assembly ~= { _.copy(includeBin = true, includeScala = false, includeDependency = false) }