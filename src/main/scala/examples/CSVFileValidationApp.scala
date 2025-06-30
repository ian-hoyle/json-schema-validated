package examples

import cats.data.Validated.*
import cats.syntax.validated.*
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSVData
import validation.Validation.validate
import validation.custom.{DebugPrintFirstRow, FailedValidation}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.jsonschema.ValidationDataUtils.{addJsonForValidation, mapKeys}
import validation.jsonschema.{ValidatedSchema, ValidationDataUtils}
import validation.{DataValidationResult, Parameters, RowData, ValidatorConfiguration}

object CSVFileValidationApp extends App {

  private val fileToValidate = if (args != null && args.length > 0) args(0) else "sample.csv"
  private val sizeMultiplier = if (args != null && args.length > 1) args(1).toInt else 10000

  private val parameters = Parameters(configFile = "config.json", baseSchema = "organisationBase.json", schema = List("organisationBase.json", "openRecord.json", "closedRecord.json"), inputAlternateKey = Some("TDRMetadataUpload"), fileToValidate = fileToValidate, idKey = Some("Filepath"), requiredSchema = None, keyToOutAlternate = Some("TDRMetadataUpload"))


  private val configuration: ValidatorConfiguration = prepareValidationConfiguration(parameters.configFile, parameters.baseSchema, parameters.inputAlternateKey)

  // Validate the data can be loaded
  private val dataLoader: DataValidationResult[List[RowData]] = loadCSVData(parameters.fileToValidate, parameters.idKey)
  private val largerDataLoader = dataLoader andThen largerDataSet(sizeMultiplier) // Generate a larger dataset for testing

  // Validations that can stop processing early
  private val failFastValidations: List[List[RowData] => DataValidationResult[List[RowData]]] = getFailFastValidations(parameters, configuration)
  // Validations that can be combined and run after the fail-fast validations
  private val combiningValidations: List[List[RowData] => DataValidationResult[List[RowData]]] = getCombiningValidations(parameters.schema, configuration)


  private val startTime = System.currentTimeMillis
  private val result = validate(largerDataLoader, failFastValidations, combiningValidations :+ FailedValidation.failedValidation)

  result match {
    case Valid(data) =>
      println("Validation successful")
    case Invalid(errors) =>
      println(s"Invalid in ${System.currentTimeMillis() - startTime} milliseconds with ${errors.length} errors")
    //        println(s"Validation failed with ${errors.length} errors:")
    //        println(errors.asJson)
  }
}


private def getCombiningValidations(schemas: List[String], validatorConfiguration: ValidatorConfiguration): List[List[RowData] => DataValidationResult[List[RowData]]] = {
  ValidatedSchema.generateSchemaValidatedList(schemas, validatorConfiguration.inputAlternateKey)
}

private def getFailFastValidations(parameters: Parameters, configuration: ValidatorConfiguration): List[List[RowData] => DataValidationResult[List[RowData]]] = {
  List(
    mapKeys(configuration.altInToKey),
    addJsonForValidation(configuration.valueMapper),
    DebugPrintFirstRow.printFirstRow,
    validateSchemaSingleRow(parameters.requiredSchema, configuration.inputAlternateKey)
  )
}

// just for testing purposes, to generate a larger dataset
private def largerDataSet(sizeMultiplier: Int)(data: List[RowData]): DataValidationResult[List[RowData]] = {
  data.zipWithIndex.flatMap { case (row, index) =>
    (1 to sizeMultiplier).map(i => row.copy(assetId = Some(s"${index * sizeMultiplier + i}_${row.assetId.getOrElse("")}")))
  }.valid
}


