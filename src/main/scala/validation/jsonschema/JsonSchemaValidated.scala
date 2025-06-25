package validation.jsonschema

import cats.data.Validated.*
import cats.data.{NonEmptyList, Validated}
import cats.effect.IO
import cats.implicits.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import validation.error.CSVValidationResult.dataValidationResultMonoid
import validation.error.ValidationErrors
import validation.{DataValidationResult, RowData}


object JsonSchemaValidated:

  def validateWithMultipleSchemaInParallel(fileValidation: DataValidationResult[List[RowData]], schemas: List[String], propertyToAll: String => String): IO[DataValidationResult[List[RowData]]] = {
    fileValidation match {
      case Valid(value) =>
        val schemaValidations = schemas.map(schema => ValidatedSchema.schemaValidated(schemaFile = schema, propertyToAlt = propertyToAll))
        val dataValidations = schemaValidations.map { validation =>
          IO(validation(value))
        }
        dataValidations.parSequence.map(_.combineAll)
      case Invalid(errors) =>
        IO.pure(errors.invalid)
    }
  }
  // The propertyToAlt function is used to map the internal property names to the original input for error reporting.
  def generateSchemaValidatedList(schemaFiles: List[String], propertyToAlt: String => String): List[List[RowData] => DataValidationResult[List[RowData]]] = {
    schemaFiles.map { schemaFile =>
      data => ValidatedSchema.schemaValidated(schemaFile, propertyToAlt)(data)
    }
  }
  
  def composeMultipleValidated(schemaFiles: List[String], propertyToAlt: String => String)(data: List[RowData]): DataValidationResult[List[RowData]] = {
    schemaFiles.map { schemaFile =>
      ValidatedSchema.schemaValidated(schemaFile, propertyToAlt)(data)
    }.combineAll
  }

  /**
   * Validates the provided data using a sequence of fail-fast validations followed by composed validations.
   *
   * @param dataLoader          The initial data to validate, wrapped in a DataValidationResult.
   * @param failFastValidations A sequence of validations to apply in order, stopping at the first failure.
   * @param composeValidations  A sequence of validations to apply after fail-fast validations, combining their results.
   * @return The result of applying all validations, as a DataValidationResult.
   */
  def validate(dataLoader: DataValidationResult[List[RowData]],
               failFastValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]],
               composeValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]]): DataValidationResult[List[RowData]] = {
    val failFastValidated = failFastValidations.foldLeft(dataLoader) {
      (acc, validate) => {
         acc.andThen(validate)
      }
    }
    failFastValidated.andThen(data => composeValidations.map(_(data)).combineAll)
  }

  def mapKeys(keyMapper: String => String)(inputData: List[RowData]): DataValidationResult[List[RowData]] = {
    val validatedData = inputData.map {
      row => row.copy(data = row.data.map { case (k, v) => keyMapper(k) -> v }) 
    }
    validatedData.valid
  }

  def addJsonForValidation(valueMapper: (String, String) => Any)(data: List[RowData]): DataValidationResult[List[RowData]] = {
    val validatedData = data.map { row =>
      val json = convertToJSONString(row.data, valueMapper)
      row.copy(json = Some(json))
    }
    validatedData.valid
  }

  private def convertToJSONString(data: Map[String, Any], valueMapper: (String, String) => Any) = {
    val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val convertedData = data.map {
      case (header, value: String) =>
        (header,
          if (value.isEmpty) null
          else valueMapper(header, value)
        )
      case (header, value) =>
        (header, value)
    }
    mapper.writeValueAsString(convertedData)
  }
