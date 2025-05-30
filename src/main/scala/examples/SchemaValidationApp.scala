package examples

import cats.data.Validated.*
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSVData
import io.circe.generic.auto.*
import io.circe.syntax.*
import validation.custom.DebugPrintFirstRow
import validation.jsonschema.JsonSchemaValidated
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, mapKeys}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.{DataValidationResult, Parameters, RowData}
import cats.implicits.*
import validation.error.CSVValidationResult.*

object SchemaValidationApp {

  def main(args: Array[String]): Unit = {

    val fileToValidate = if (args.length > 0) args(0) else "sample.csv"

    val params = Parameters(
      configFile = "config.json",
      schema = List("organisationBase.json", "openRecord.json", "closedRecord.json"),
      alternateKey = Some("TDRMetadataUpload"),
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
    val configuration = prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)

    val combiningValidations: List[List[RowData] => DataValidationResult[List[RowData]]] = JsonSchemaValidated.generateSchemaValidatedList(parameters.schema, configuration.keyToAltIn)

    def combineValidations(validations: List[List[RowData] => DataValidationResult[List[RowData]]])(inputData: List[RowData]): DataValidationResult[List[RowData]] = {
      validations.map(validation => validation(inputData)).combineAll
    }

    def validate(dataLoader: DataValidationResult[List[RowData]], validations: Seq[List[RowData] => DataValidationResult[List[RowData]]]): DataValidationResult[List[RowData]] = {
      validations.foldLeft(dataLoader) {
        (acc, validate) => acc.andThen(validate)
      }
    }

    val validations = Seq(
      mapKeys(configuration.altInToKey) _,
      addJsonForValidation(configuration.valueMapper) _,
      DebugPrintFirstRow.printFirstRow,
      validateSchemaSingleRow(parameters.requiredSchema, configuration.keyToAltIn) _,
      combineValidations(combiningValidations) _
    )
    val dataLoader = loadCSVData(parameters.fileToValidate,parameters.idKey)


    validate(dataLoader,validations)

  }

}
