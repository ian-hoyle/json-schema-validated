package examples

import cats.data.Validated.*
import cats.syntax.validated.*
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.{CSVLoader, JsonLoader}
import validation.*
import validation.Validation.validate
import io.circe.generic.auto.*
import io.circe.syntax.*
import validation.custom.{CustomJsonValidation, DebugPrintFirstRow, FailedValidation}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.jsonschema.ValidationDataUtils.{addJsonForValidation, mapKeys}
import validation.jsonschema.{ValidatedSchema, ValidationDataUtils}

object CSVFileValidationApp extends App {

  private val fileToValidate = if (args != null && args.length > 0) args(0) else "sample.csv"
  private val sizeMultiplier = if (args != null && args.length > 1) args(1).toInt else 10000

  private val parameters = Parameters(
    configFile = "config.json",
    baseSchema = "organisationBase.json",
    schema = List("organisationBase.json", "openRecord.json", "closedRecord.json"),
    fileToValidate = fileToValidate,
    idKey = Some("Filepath"),
    requiredSchema = None,
    keyToOutAlternate = Some("TDRMetadataUpload")
  )

  private val configuration: ValidatorConfiguration = prepareValidationConfiguration(
    parameters.configFile,
    parameters.baseSchema
  )

  // Validate the data can be loaded
  private val loadedCSV: DataValidation =
    CSVLoader.loadCSVData(parameters.fileToValidate, parameters.idKey)
  private val loadedJSON = JsonLoader.loadJsonListData("sampleJsonList.json", parameters.idKey)
  private val largerDataLoader =
    loadedCSV andThen largerDataSet(sizeMultiplier) // Generate a larger dataset for testing

  // Validations that can stop processing early
  private val failFastValidations: List[List[Data] => DataValidation] =
    getFailFastValidations(parameters, configuration)
  // Validations that can be combined and run after the fail-fast validations
  private val combiningValidations: List[List[Data] => DataValidation] =
    getCombiningValidations(parameters.schema, configuration)
  private val customJsonValidation = CustomJsonValidation.validateClosureFields(configuration.propertyToDomainKey("TDRMetadataUpload"))

  private val startTime = System.currentTimeMillis
  private val result = validate(
    loadedCSV,
    failFastValidations,
    combiningValidations :+ FailedValidation.failedValidation :+ customJsonValidation
  )

  result match {
    case Valid(data) =>
      println("Validation successful")
    case Invalid(errors) =>
      println(
        s"Invalid in ${System.currentTimeMillis() - startTime} milliseconds with ${errors.length} errors"
      )
      println(errors.asJson)
  }
}

private def getCombiningValidations(
    schemas: List[String],
    validatorConfiguration: ValidatorConfiguration
): List[List[Data] => DataValidation] = {
  ValidatedSchema.generateSchemaValidatedList(schemas, validatorConfiguration.propertyToDomainKey("TDRMetadataUpload"))
}

private def getFailFastValidations(
    parameters: Parameters,
    configuration: ValidatorConfiguration
): List[List[Data] => DataValidation] = {
  List(
    mapKeys(configuration.domainKeyToProperty("TDRMetadataUpload")),
    addJsonForValidation(configuration.valueMapper),
    DebugPrintFirstRow.printFirstRow,
    validateSchemaSingleRow(parameters.requiredSchema, configuration.propertyToDomainKey("TDRMetadataUpload"))
  )
}

// just for testing purposes, to generate a larger dataset
private def largerDataSet(
    sizeMultiplier: Int
)(data: List[Data]): DataValidation = {
  data.zipWithIndex.flatMap { case (row, index) =>
    (1 to sizeMultiplier).map(i => row.copy(assetId = Some(s"${index * sizeMultiplier + i}_${row.assetId.getOrElse("")}")))
  }.valid
}
