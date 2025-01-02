package validation

import cats.data.Reader
import cats.data.Validated.*
import cats.effect.IO
import cats.implicits.*
import cats.data.NonEmptyList


import config.CSVParserConfig
import csv.{CSVUtils, RowData}
import validation.jsonschema.ValidatedSchema
import validation.jsonschema.ValidatedSchema.CSVValidationResult
import error.CSVValidationResult.combineCSVValidationResult


object CSVValidator:

  def validationProgram(parameters: Parameters): IO[CSVValidationResult[List[RowData]]] = {

    val initialValidation: CSVValidationResult[List[RowData]] =
      prepareCSVConfiguration(parameters)
        .andThen(CSVUtils.loadCSVData)
        .andThen(ValidatedSchema.requiredSchemaValidated(parameters.requiredSchema))

    initialValidation match {
      case Valid(value) =>
        val schemaValidations = parameters.schema.map(x=>ValidatedSchema.schemaValidated(Some(x)))
        val ioValidations = schemaValidations.map { validation =>
          IO(validation(value))
        }
        // add other parallel validations here to ioValidations
        ioValidations.parSequence.map(_.combineAll)
      case Invalid(errors) =>
        IO.pure(errors.invalid)
    }
  }


  def prepareCSVConfiguration(parameters: Parameters): CSVValidationResult[CSVConfiguration] = {
    val csvConfigurationReader = for {
      altToPropertyMapper <- Reader(CSVParserConfig.alternateKeyToPropertyMapper)
      propertyToAltMapper <- Reader(CSVParserConfig.propertyToAlternateKeyMapper)
      valueMapper <- Reader(CSVParserConfig.csvStringToValueMapper)
    } yield CSVConfiguration(altToPropertyMapper, propertyToAltMapper, valueMapper, parameters.csvFile, parameters.idKey)
    csvConfigurationReader.run(parameters).valid
  }


case class CSVConfiguration(altToProperty: String => String,
                            propertyToAlt: String => String,
                            valueMapper: String => String => Any,
                            csv: String,
                            idKey: Option[String] = None)

case class Parameters(csConfig: String,
                      schema: List[String],
                      alternates: Option[String],
                      csvFile: String,
                      idKey: Option[String],
                      requiredSchema:Option[String] = None)