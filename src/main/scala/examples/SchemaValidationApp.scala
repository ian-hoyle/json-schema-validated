package examples

import cats.data.Validated.*
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSVData
import io.circe.generic.auto.*
import io.circe.syntax.*
import validation.custom.DebugPrintFirstRow
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, composeMultipleValidated, mapKeys}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.{DataValidationResult, Parameters, RowData}

object SchemaValidationApp {

  def main(args: Array[String]): Unit = {

    val params = Parameters(
      configFile ="config.json",
      schema = List("organisationBase.json", "openRecord.json"),
      alternateKey = Some("TDRMetadataUpload"),
      fileToValidate = "sample.csv",
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
