import AssemblyKeys._

assemblySettings

name := "mqperf"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.9.25" exclude("commons-logging", "commons-logging"),
  "org.json4s" %% "json4s-native" % "3.2.11",
  "org.mongodb" % "mongo-java-driver" % "2.13.0",
  "com.rabbitmq" % "amqp-client" % "3.5.0",
  "org.hornetq" % "hornetq-native" % "2.4.5.Final" from "http://repo1.maven.org/maven2/org/hornetq/hornetq-native/2.4.5.Final/hornetq-native-2.4.5.Final.jar",
  "org.hornetq" % "hornetq-core-client" % "2.4.5.Final" from "http://repo1.maven.org/maven2/org/hornetq/hornetq-core-client/2.4.5.Final/hornetq-core-client-2.4.5.Final.jar",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.10",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.10",
  "org.apache.kafka" %% "kafka" % "0.8.2.1"
    exclude("javax.jms", "jms")
    exclude("com.sun.jdmk", "jmxtools")
    exclude("com.sun.jmx", "jmxri")
    exclude("log4j", "log4j")
    exclude("org.slf4j", "slf4j-log4j12"),
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "com.geteventstore" %% "eventstore-client" % "2.0.2"
)

assemblyOption in assembly ~= { _.copy(includeBin = true, includeScala = false, includeDependency = false) }

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList(ps@_*) if ps.last == "HornetQUtilBundle_$bundle.class" => MergeStrategy.first
  case x => old(x)
}
}