name := "akka-stream"

version := "1.0.0"

scalaVersion := "2.13.8"

lazy val akkaVersion = "2.6.19"
lazy val scalaTestVersion = "3.2.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
  "org.scalatest"     %% "scalatest" % scalaTestVersion
)

