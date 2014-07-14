import AssemblyKeys._

assemblySettings

name := "mqperf"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.7.12" exclude("commons-logging", "commons-logging"),
  "org.json4s" %% "json4s-native" % "3.2.9",
  "org.mongodb" % "mongo-java-driver" % "2.12.2",
  "com.rabbitmq" % "amqp-client" % "3.3.2",
  "org.hornetq" % "hornetq-native" % "2.4.0.Final" from "http://repo1.maven.org/maven2/org/hornetq/hornetq-native/2.4.0.Final/hornetq-native-2.4.0.Final.jar",
  "org.hornetq" % "hornetq-core-client" % "2.4.0.Final" from "http://repo1.maven.org/maven2/org/hornetq/hornetq-core-client/2.4.0.Final/hornetq-core-client-2.4.0.Final.jar",
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.7",
  "org.apache.kafka" %% "kafka" % "0.8.1.1"
    exclude("javax.jms", "jms")
    exclude("com.sun.jdmk", "jmxtools")
    exclude("com.sun.jmx", "jmxri")
    exclude("log4j", "log4j"),
  "org.scalatest" %% "scalatest" % "2.1.3" % "test"
)

assemblyOption in assembly ~= { _.copy(includeBin = true, includeScala = false, includeDependency = false) }

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
  case PathList(ps@_*) if ps.last == "HornetQUtilBundle_$bundle.class" => MergeStrategy.first
  case x => old(x)
}
}