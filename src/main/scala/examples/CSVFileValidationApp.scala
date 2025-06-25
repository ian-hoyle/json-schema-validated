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

    val parameters = Parameters(
      configFile = "config.json",
      schema = List("organisationBase.json", "openRecord.json", "closedRecord.json"),
      inputAlternateKey = Some("TDRMetadataUpload"),
      fileToValidate = fileToValidate,
      idKey = Some("Filepath"),
      requiredSchema = None,
      keyToOutAlternate = Some("TDRMetadataUpload"))


    val configuration: ValidatorConfiguration = prepareValidationConfiguration(parameters.configFile, parameters.inputAlternateKey)

    // Validate the data can be loaded
    val dataLoader: DataValidationResult[List[RowData]] = loadCSVData(parameters.fileToValidate, parameters.idKey)
    // Validations that can stop processing early
    val failFastValidations: List[List[RowData] => DataValidationResult[List[RowData]]] = getFailFastValidations(parameters, configuration)
    // Validations that can be combined and run after the fail-fast validations
    val combiningValidations: List[List[RowData] => DataValidationResult[List[RowData]]] = getCombiningValidations(parameters.schema, configuration)


    val result = validate(dataLoader, failFastValidations, combiningValidations:+ FailedValidation.failedValidation)

    result match {
      case Valid(data) =>
        println("Validation successful")
      case Invalid(errors) =>
        println(s"Validation failed with ${errors.length} errors:")
        println(errors.asJson)
    }
  }



  private def getCombiningValidations(schemas:List[String], validatorConfiguration: ValidatorConfiguration) : List[List[RowData] => DataValidationResult[List[RowData]]] = {
    JsonSchemaValidated.generateSchemaValidatedList(schemas, validatorConfiguration.inputAlternateKey)
  }

  private def getFailFastValidations( parameters: Parameters,configuration: ValidatorConfiguration): List[List[RowData] => DataValidationResult[List[RowData]]] = {
    List(
      mapKeys(configuration.altInToKey),
      addJsonForValidation(configuration.valueMapper),
      DebugPrintFirstRow.printFirstRow,
      validateSchemaSingleRow(parameters.requiredSchema, configuration.inputAlternateKey)
    )
  }
}
