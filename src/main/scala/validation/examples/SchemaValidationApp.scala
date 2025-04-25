package validation.examples

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import validation.config.ValidationConfig.prepareValidationConfiguration
import validation.datalaoader.CSVLoader.{loadCSV, loadCSVData}
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, composeMultipleValidated, validateWithMultipleSchemaInParallel}
import validation.jsonschema.ValidatedSchema.{schemaValidated, validateSchemaSingleRow}
import validation.{DataValidationResult, Parameters, RowData}

object SchemaValidationApp {

  def main(args: Array[String]): Unit = {

    val params = Parameters("config.json",
      List("TDRMetadataUploadorganisationBase.json",
        "TDRMetadataUploadopenRecord.json"),
      None, "sample.csv", Some("Filepath"), None, Some("TDRMetadataUpload"))

    val paramsString = params.asJson.noSpaces


    decode[Parameters](paramsString) match {
      case Right(params) =>
        val result = IO(csvFileValidationSync(params)).unsafeRunSync()
        result match {
          case cats.data.Validated.Valid(data) =>
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
      configuration <- IO(prepareValidationConfiguration(parameters.configFile, parameters.alternateKey))
      data <- IO(
        loadCSVData(parameters.fileToValidate, parameters.idKey)
          andThen addJsonForValidation(configuration.valueMapper)
          andThen validateSchemaSingleRow(parameters.requiredSchema, (x: String) => x)
          andThen schemaValidated(parameters.requiredSchema.get, (x: String) => x)
      )
      validation <- validateWithMultipleSchemaInParallel(data, parameters.schema, (x: String) => x)
    } yield validation
  }

  private def csvFileValidationSync(parameters: Parameters): DataValidationResult[List[RowData]] = {

    val configuration = prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)
    loadCSVData(parameters.fileToValidate, parameters.idKey)
      .andThen(addJsonForValidation(configuration.valueMapper))
      .andThen(validateSchemaSingleRow(parameters.requiredSchema, (x: String) => x))
      .andThen(composeMultipleValidated(parameters.schema, (x: String) => x))
  }
  
//  private def streamValidation(parameters: Parameters): DataValidationResult[List[RowData]] = {
//    val configuration = prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)
//    val stream:List[RowData] = loadCSV(parameters.fileToValidate, parameters.idKey)
//    val rowDataSource = 
//    
//   
//  }
}