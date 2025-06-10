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

  def combineValidations(validations: List[List[RowData] => DataValidationResult[List[RowData]]])(inputData: List[RowData]): DataValidationResult[List[RowData]] = {
    validations.map(validation => validation(inputData)).combineAll
  }

  def validate(dataLoader: DataValidationResult[List[RowData]], validations: Seq[List[RowData] => DataValidationResult[List[RowData]]]): DataValidationResult[List[RowData]] = {
    validations.foldLeft(dataLoader) {
      (acc, validate) => acc.andThen(validate)
    }
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
