import sbt._
import sbt.Keys._

import scalariform.formatter.preferences._

lazy val commonSettings = Seq(
  version := "2.0",
  scalaVersion := "2.11.11"
) ++ SbtScalariform.scalariformSettings ++ Seq(
  scalariformPreferences := scalariformPreferences.value
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(CompactControlReadability, true)
    .setPreference(SpacesAroundMultiImports, false))

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    // This way we kill 'zombie' senders and receivers after unsuccessful test(s)
    fork in Test := true
  )

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

name := "mqperf"

libraryDependencies ++= Seq(
  "com.fasterxml.uuid"          %   "java-uuid-generator"   % "3.1.4",
  "org.scala-lang.modules"      %%  "scala-java8-compat"    % "0.8.0"     % "test",
  "com.amazonaws"               %   "aws-java-sdk"          % "1.11.126"   exclude("commons-logging", "commons-logging"),
  "org.json4s"                  %%  "json4s-native"         % "3.5.2",
  "org.mongodb"                 %   "mongodb-driver"        % "3.4.2",
  "com.rabbitmq"                %   "amqp-client"           % "4.1.0",
  "org.hornetq"                 %   "hornetq-native"        % "2.4.7.Final" from "http://repo1.maven.org/maven2/org/hornetq/hornetq-native/2.4.7.Final/hornetq-native-2.4.7.Final.jar",
  "org.hornetq"                 %   "hornetq-core-client"   % "2.4.7.Final" exclude("org.hornetq", "hornetq-native")
                                                                            exclude("org.apache.geronimo.specs", "geronimo-jms_1.1_spec"),
  "com.typesafe.scala-logging"  %%  "scala-logging"         % "3.5.0",
  "ch.qos.logback"              %   "logback-classic"       % "1.2.3",
  "org.slf4j"                   %   "jcl-over-slf4j"        % "1.7.25",
  "org.slf4j"                   %   "log4j-over-slf4j"      % "1.7.25",
  "org.apache.kafka"            %   "kafka-clients"         % "0.10.2.1"  exclude("javax.jms", "jms")
                                                                          exclude("com.sun.jdmk", "jmxtools")
                                                                          exclude("com.sun.jmx", "jmxri")
                                                                          exclude("log4j", "log4j")
                                                                          exclude("org.slf4j", "slf4j-log4j12"),
  "org.scalatest"               %%  "scalatest"             % "3.0.3"     % "test",
  "com.geteventstore"           %%  "eventstore-client"     % "4.1.0",
  "org.apache.activemq"         %   "activemq-client"       % "5.14.5"    exclude("org.apache.geronimo.specs", "geronimo-jms_1.1_spec"),
  "com.typesafe"                %   "config"                % "1.3.1",
  "io.dropwizard.metrics"       %   "metrics-core"          % "3.2.1",
  "org.apache.activemq"         %   "artemis-jms-client"    % "2.0.0"     exclude("commons-logging", "commons-logging")
)

assemblyOption in assembly ~= {
  _.copy(includeBin = true, includeScala = false, includeDependency = false)
}

assemblyMergeStrategy in assembly := {
  case PathList(ps@_*) if ps.last == "HornetQUtilBundle_$bundle.class" => MergeStrategy.first
  case x => (assemblyMergeStrategy in assembly).value(x)
}
