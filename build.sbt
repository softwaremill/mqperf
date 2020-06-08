import sbt._
import sbt.Keys._

lazy val commonSettings = Seq(
  version := "2.0",
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
  "org.hornetq" % "hornetq-native" % "2.4.7.Final" from "https://repo1.maven.org/maven2/org/hornetq/hornetq-native/2.4.7.Final/hornetq-native-2.4.7.Final.jar",
  "org.hornetq" % "hornetq-core-client" % "2.4.7.Final" exclude ("org.hornetq", "hornetq-native")
    exclude ("org.apache.geronimo.specs", "geronimo-jms_1.1_spec"),
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.30",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.30",
  "org.apache.kafka" % "kafka-clients" % "2.5.0" exclude ("javax.jms", "jms")
    exclude ("com.sun.jdmk", "jmxtools")
    exclude ("com.sun.jmx", "jmxri")
    exclude ("log4j", "log4j")
    exclude ("org.slf4j", "slf4j-log4j12"),
  "org.scalatest" %% "scalatest" % "3.1.2" % Test,
  "com.geteventstore" %% "eventstore-client" % "7.1.0",
  "org.apache.activemq" % "activemq-client" % "5.15.12" exclude ("org.apache.geronimo.specs", "geronimo-jms_1.1_spec"),
  "com.typesafe" % "config" % "1.4.0",
  "org.apache.activemq" % "artemis-jms-client" % "2.13.0" exclude ("commons-logging", "commons-logging"),
  "org.apache.rocketmq" % "rocketmq-client" % "4.7.0",
  "com.softwaremill.kmq" %% "core" % "0.2",
  "io.prometheus" % "simpleclient" % prometheusVersion,
  "io.prometheus" % "simpleclient_common" % prometheusVersion
)

assemblyOption in assembly ~= {
  _.copy(includeBin = true, includeScala = false, includeDependency = false)
}

assemblyMergeStrategy in assembly := {
  case PathList(ps @ _*) if ps.last == "HornetQUtilBundle_$bundle.class" => MergeStrategy.first
  case x                                                                 => (assemblyMergeStrategy in assembly).value(x)
}
