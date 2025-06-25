package examples

import cats.data.Validated.*
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSVData
import io.circe.generic.auto.*
import io.circe.syntax.*
import validation.custom.{DebugPrintFirstRow, FailedValidation}
import validation.jsonschema.JsonSchemaValidated
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, composeMultipleValidated, mapKeys, validate}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.{DataValidationResult, Parameters, RowData, ValidatorConfiguration}
import validation.error.CSVValidationResult.*

object CSVFileValidationApp {

  def main(args: Array[String]): Unit = {

    val fileToValidate = if (args.length > 0) args(0) else "sample.csv"

    val params = Parameters(
      configFile = "config.json",
      schema = List("organisationBase.json", "openRecord.json", "closedRecord.json"),
      inputAlternateKey = Some("TDRMetadataUpload"),
      fileToValidate = fileToValidate,
      idKey = Some("Filepath"),
      requiredSchema = None,
      keyToOutAlternate = Some("TDRMetadataUpload"))

    val result = csvFileValidation(params)

    result match {
      case Valid(data) =>
        println("Validation successful")
      //data.foreach(row => println(row))
      case Invalid(errors) =>
        println(s"Validation failed with ${errors.length} errors:")
        println(errors.asJson)
    }
  }


  private def csvFileValidation(parameters: Parameters): DataValidationResult[List[RowData]] = {
    val configuration: ValidatorConfiguration = prepareValidationConfiguration(parameters.configFile, parameters.inputAlternateKey)

    val combiningValidations: List[List[RowData] => DataValidationResult[List[RowData]]] = JsonSchemaValidated.generateSchemaValidatedList(parameters.schema, configuration.inputAlternateKey)

    val failFastValidations: List[List[RowData] => DataValidationResult[List[RowData]]] = List(
      mapKeys(configuration.altInToKey),
     // FailedValidation.failedValidation,
      addJsonForValidation(configuration.valueMapper),
      DebugPrintFirstRow.printFirstRow,
      validateSchemaSingleRow(parameters.requiredSchema, configuration.inputAlternateKey)
    )


    val dataLoader: DataValidationResult[List[RowData]] = loadCSVData(parameters.fileToValidate,parameters.idKey)

    validate(dataLoader, failFastValidations, combiningValidations:+ FailedValidation.failedValidation)
    
  }
}
