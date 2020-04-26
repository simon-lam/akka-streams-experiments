name := "akka-streams-experiments"

scalaVersion := "2.12.10"

val akkaVersion = "2.6.4"

libraryDependencies ++= Seq(
  "com.softwaremill.macwire" %% "macros" % "2.3.0" ,
  "com.softwaremill.macwire" %% "util" % "2.3.0",

  "com.typesafe.akka" %% "akka-stream" % akkaVersion,

  // Pull in pie models, Akka streams, logging, etc
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
)