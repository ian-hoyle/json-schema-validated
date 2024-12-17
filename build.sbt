import Dependencies.*

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"

lazy val root = (project in file("."))
  .settings(
    name := "mycsvvalidator"
  )
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test

val circeVersion = "0.14.9"

libraryDependencies ++= Seq(
  commonsLang3,
  ujson,
  jsonSchemaValidator,
  pekkoTestKit % Test,
  catsEffect,
  nscalaTime
)

scalacOptions ++= Seq("-Xmax-inlines", "50")
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)
