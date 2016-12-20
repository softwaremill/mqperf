
lazy val commonSettings = Seq(
  version := "2.0",
  scalaVersion := "2.11.8"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*)

lazy val oracleaq = project.in(file("oracleaq")).
  dependsOn(root).
  settings(commonSettings: _*).
  settings(name := "mqperfext").
  settings(libraryDependencies ++= Seq(
    "com.oracle" % "aqapi_2.11" % "1.0.0",
    "com.oracle" % "ojdbc6_2.11" % "1.0.0",
    "javax.transaction" % "jta" % "1.1"
  )
  )

name := "mqperf"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.11.68" exclude("commons-logging", "commons-logging"),
  "org.json4s" %% "json4s-native" % "3.5.0",
  "org.mongodb" % "mongodb-driver" % "3.4.0",
  "com.rabbitmq" % "amqp-client" % "3.5.0",
  "org.hornetq" % "hornetq-native" % "2.4.7.Final" from "http://repo1.maven.org/maven2/org/hornetq/hornetq-native/2.4.7.Final/hornetq-native-2.4.7.Final.jar",
  "org.hornetq" % "hornetq-core-client" % "2.4.7.Final" exclude("org.hornetq", "hornetq-native"),
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "org.slf4j" % "jcl-over-slf4j" % "1.7.22",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.22",
  "org.apache.kafka" %% "kafka" % "0.10.1.0"
    exclude("javax.jms", "jms")
    exclude("com.sun.jdmk", "jmxtools")
    exclude("com.sun.jmx", "jmxri")
    exclude("log4j", "log4j")
    exclude("org.slf4j", "slf4j-log4j12"),
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "com.geteventstore" %% "eventstore-client" % "3.0.4",
  "org.apache.activemq" % "activemq-client" % "5.14.2",
  "com.typesafe" % "config" % "1.3.1"
)

assemblyOption in assembly ~= { _.copy(includeBin = true, includeScala = false, includeDependency = false) }

assemblyMergeStrategy in assembly := {
  case PathList(ps@_*) if ps.last == "HornetQUtilBundle_$bundle.class" => MergeStrategy.first
  case x => (assemblyMergeStrategy in assembly).value(x)
}
