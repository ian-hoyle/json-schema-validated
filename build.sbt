import Dependencies.*
import sbtassembly.AssemblyPlugin.autoImport.*

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.4"

lazy val root = (project in file("."))
  .settings(
    name := "mycsvvalidator"
  )
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test

assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

val circeVersion = "0.14.9"

libraryDependencies ++= Seq(
  commonsLang3,
  ujson,
  jsonSchemaValidator,
  scalaCsv,
  jacksonModule,
  catsEffect,
  awsLambda,
  awsLambdaJavaEvents,
  nscalaTime,
  awsS3
)

scalacOptions ++= Seq("-Xmax-inlines", "50")
libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)
