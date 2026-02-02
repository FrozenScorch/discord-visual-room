name := "discord-visual-room-backend"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.12"

val AkkaVersion = "2.8.5"
val AkkaHttpVersion = "10.5.3"
val Discord4JVersion = "3.2.6"
val LogbackVersion = "1.4.11"
val ScalaLoggingVersion = "3.9.5"
val Json4sVersion = "4.0.6"

libraryDependencies ++= Seq(
  // Akka Actor
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,

  // Akka Streams
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,

  // Akka HTTP for WebSocket
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,

  // Discord integration
  "com.discord4j" % "discord4j-core" % Discord4JVersion,

  // Logging
  "ch.qos.logback" % "logback-classic" % LogbackVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % ScalaLoggingVersion,
  "ch.qos.logback.contrib" % "logback-json-classic" % "0.1.5",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.2",

  // JSON serialization
  "org.json4s" %% "json4s-jackson" % Json4sVersion,
  "org.json4s" %% "json4s-ext" % Json4sVersion,

  // HTTP client for LLM
  "com.softwaremill.sttp.client3" %% "core" % "3.9.0",

  // Configuration
  "com.typesafe" % "config" % "1.4.3",

  // Testing
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.17" % Test
)

// Compiler options
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard"
)

// Fork JVM for better logging
fork := true
