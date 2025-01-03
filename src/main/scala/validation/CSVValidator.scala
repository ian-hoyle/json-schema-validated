package validation

import cats.data.Validated.*
import cats.data.{NonEmptyList, Reader, Validated}
import cats.effect.IO
import cats.implicits.*
import config.CSVParserConfig
import csv.{CSVUtils, RowData}
import error.CSVValidationResult.combineCSVValidationResult
import error.ValidationErrors
import validation.jsonschema.ValidatedSchema
import validation.jsonschema.ValidatedSchema.CSVValidationResult


object CSVValidator:

  def validationProgram(parameters: Parameters): IO[CSVValidationResult[List[RowData]]] = {
    for {
      configuration <- prepareCSVConfiguration(parameters)
      fileValidation <- fileValidations(configuration)
      validation <- dataValidation(fileValidation, configuration.schema)
    } yield validation
  } //TODO handle error with CSVValidationResult[List[RowData]]

  private def fileValidations(csvConfiguration: CSVValidatorConfiguration): IO[CSVValidationResult[List[RowData]]] = {
    IO({
      // UTF 8 check to be added first
      CSVUtils.loadCSVData(csvConfiguration)
        .andThen(ValidatedSchema.requiredSchemaValidated(csvConfiguration.requiredSchema))
    })
  }

  def prepareCSVConfiguration(parameters: Parameters): IO[CSVValidatorConfiguration] = {
    IO({
      val csvConfigurationReader = for {
        altHeaderToPropertyMapper <- Reader(CSVParserConfig.alternateKeyToPropertyMapper)
        propertyToAltHeaderMapper <- Reader(CSVParserConfig.propertyToAlternateKeyMapper)
        valueMapper <- Reader(CSVParserConfig.csvStringToValueMapper)
      } yield CSVValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper,
        valueMapper, parameters.csvFile, parameters.idKey,
        parameters.requiredSchema, parameters.schema)
      csvConfigurationReader.run(parameters)
    }
    ) //TODO handle error with raiseError that contains ValidationResult
  }

  private def dataValidation(fileValidation: CSVValidationResult[List[RowData]], schema: List[String]): IO[CSVValidationResult[List[RowData]]] = {
    fileValidation match {
      case Valid(value) =>
        val schemaValidations = schema.map(x => ValidatedSchema.schemaValidated(Some(x)))
        val dataValidations = schemaValidations.map { validation =>
          IO(validation(value))
        }
        // add other data validations here to dataValidations
        dataValidations.parSequence.map(_.combineAll)
      case Invalid(errors) =>
        IO.pure(errors.invalid)
    }
  }


case class CSVValidatorConfiguration(altToProperty: String => String,
                                     propertyToAlt: String => String,
                                     valueMapper: String => String => Any,
                                     csvFile: String,
                                     idKey: Option[String],
                                     requiredSchema: Option[String],
                                     schema: List[String]
                                    )
// Comes from arguments
case class Parameters(csConfig: String,
                      schema: List[String],
                      alternates: Option[String],
                      csvFile: String,
                      idKey: Option[String],
                      requiredSchema: Option[String] = None)