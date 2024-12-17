import sbt.*

object Dependencies {
  private val pekkoVersion = "1.1.2"

  lazy val commonsLang3 = "org.apache.commons" % "commons-lang3" % "3.17.0"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
  lazy val ujson = "com.lihaoyi" %% "upickle" % "4.0.2"
  lazy val jacksonModule = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.18.2"
  lazy val metadataSchema = "uk.gov.nationalarchives" % "da-metadata-schema_3" % "0.0.42"
  lazy val jsonSchemaValidator = "com.networknt" % "json-schema-validator" % "1.5.4"
  lazy val pekkoActor = "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion
  lazy val pekkoConnectors = "org.apache.pekko" %% "pekko-connectors-csv" % pekkoVersion
  lazy val pekkoStream = "org.apache.pekko" %% "pekko-stream" % pekkoVersion
  lazy val pekkoTestKit = "org.apache.pekko" %% "pekko-testkit" % pekkoVersion
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.7"
  lazy val nscalaTime = "com.github.nscala-time" %% "nscala-time" % "2.34.0"

}
