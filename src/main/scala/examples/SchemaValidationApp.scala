package examples

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import cats.data.Validated.*
import config.ValidationConfig.prepareValidationConfiguration
import validation.custom.DebugPrintFirstRow
import datalaoader.CSVLoader.loadCSVData
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, composeMultipleValidated, mapKeys, validateWithMultipleSchemaInParallel}
import validation.jsonschema.ValidatedSchema.{schemaValidated, validateSchemaSingleRow}
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
    loadCSVData(parameters.fileToValidate, parameters.idKey)             // Load the CSV data with each row key being the value of the idKey column
      .andThen(mapKeys(configuration.altInToKey))                        // The data key (column header) is converted to the alternate key using the configuration
      .andThen(addJsonForValidation(configuration.valueMapper))          // Add JSON representation of the data for validation
      .andThen (DebugPrintFirstRow.printFirstRow)                        // Debug print the first row of data for debugging purposes
      .andThen(validateSchemaSingleRow(parameters.requiredSchema, configuration.keyToAltIn)) // Validate the data against the required schema, converting the data key to the column header for the error reporting
      .andThen(composeMultipleValidated(parameters.schema, configuration.keyToAltIn)) // Validate the data against multiple schema (combining errors), converting the data key to the column header for the error reporting
  }

}
