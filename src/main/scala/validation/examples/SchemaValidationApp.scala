package validation.examples

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import validation.config.ValidationConfig.prepareValidationConfiguration
import validation.datalaoader.CSVLoader.loadCSVData
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, validateWithMultipleSchema}
import validation.jsonschema.ValidatedSchema.validateRequiredSchema
import validation.{DataValidationResult, Parameters, RowData}

object SchemaValidationApp {

  def main(args: Array[String]): Unit = {
//    if (args.length < 1) {
//      println("Usage: SchemaValidationApp <parameters-json>")
//      System.exit(1)
//    }
//    val paramsJson = args(0)

    val params = Parameters("config.json",
     List("TDRMetadataUploadorganisationBase.json",
        "TDRMetadataUploadopenRecord.json"),
      None, "sample.csv", Some("Filepath"),None, Some("TDRMetadataUpload"))

    val paramsString = params.asJson.noSpaces


    decode[Parameters](paramsString) match {
      case Right(params) =>
        val result = csvFileValidation(params).unsafeRunSync()
        result match {
          case cats.data.Validated.Valid(data) =>
            println()
            println("Validation successful")
            data.foreach(row => println(row))
          case cats.data.Validated.Invalid(errors) =>

            println("Validation failed with errors:")
            println(errors.asJson)
            errors.toList.foreach(error => println(error))
        }
      case Left(error) =>
        println(s"Invalid input: ${error.getMessage}")
    }
  }

  def csvFileValidation(parameters: Parameters): IO[DataValidationResult[List[RowData]]] = {
    for {
      configuration <- prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)
      data <- IO(
        loadCSVData(parameters.fileToValidate, parameters.idKey)
          andThen addJsonForValidation(configuration.valueMapper)
          andThen validateRequiredSchema(parameters.requiredSchema, (x: String) => x)
      )
      validation <- validateWithMultipleSchema(data, parameters.schema, (x: String) => x)
    } yield validation
  }
}