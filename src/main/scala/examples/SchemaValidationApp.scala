package examples

import cats.data.Validated.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSVData
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import validation.custom.DebugPrintFirstRow
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, composeMultipleValidated, mapKeys}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.{DataValidationResult, Parameters, RowData}

object SchemaValidationApp {

  def main(args: Array[String]): Unit = {

    val params = Parameters("config.json",
      List("organisationBase.json",
        "openRecord.json"),
      Some("TDRMetadataUpload"), "sample.csv", Some("Filepath"), None, Some("TDRMetadataUpload"))

    val paramsString = params.asJson.noSpaces


    decode[Parameters](paramsString) match {
      case Right(params) =>
        val result = IO(csvFileValidation(params)).unsafeRunSync()
        result match {
          case Valid(data) =>
            println("Validation successful")
            //data.foreach(row => println(row))
          case Invalid(errors) =>
            println(s"Validation failed with ${errors.length} errors:")
            println(errors.asJson)
            //errors.toList.foreach(error => println(error))
        }
      case Left(error) =>
        println(s"Invalid input: ${error.getMessage}")
    }
  }


  private def csvFileValidation(parameters: Parameters): DataValidationResult[List[RowData]] = {
    val configuration = prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)

    val validations = Seq(
      mapKeys(configuration.altInToKey)_,
      addJsonForValidation(configuration.valueMapper)_,
      DebugPrintFirstRow.printFirstRow _,
      validateSchemaSingleRow(parameters.requiredSchema, configuration.keyToAltIn)_,
      composeMultipleValidated(parameters.schema, configuration.keyToAltIn)_
    )

    validations.foldLeft(loadCSVData(parameters.fileToValidate, parameters.idKey)) {
      (acc, validate) => acc.andThen(validate)
    }
  }

}
