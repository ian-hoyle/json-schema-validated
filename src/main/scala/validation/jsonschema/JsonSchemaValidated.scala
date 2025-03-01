package validation.jsonschema

import cats.data.Validated.*
import cats.data.{NonEmptyList, Validated}
import cats.effect.IO
import cats.implicits.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import validation.{DataValidationResult, RowData}
import validation.error.CSVValidationResult.combineCSVValidationResult
import validation.error.ValidationErrors


object JsonSchemaValidated:

  def validateWithSchema(fileValidation: DataValidationResult[List[RowData]], schema: String): IO[DataValidationResult[List[RowData]]] = {
    fileValidation match {
      case Valid(value) => IO(ValidatedSchema.schemaValidated(schema)(value))
      case Invalid(errors) => IO.pure(errors.invalid)
    }
  }

  def validateWithMultipleSchema(fileValidation: DataValidationResult[List[RowData]], schemas: List[String], propertyToAll: String => String): IO[DataValidationResult[List[RowData]]] = {
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


  def mapKeys(keyMapper: String => String)(inputData: List[RowData]): DataValidationResult[List[RowData]] = {
    val validatedData = inputData.map { row => row.copy(data = row.data.map { case (k, v) => keyMapper(k) -> v }) }
    validatedData.valid
  }

  def addJsonForValidation(valueMapper: (String, String) => Any)(data: List[RowData]): DataValidationResult[List[RowData]] = {
    val validatedData = data.map { row =>
      val json = convertToJSONString(row.data, valueMapper)
      row.copy(json = Some(json))
    }
    validatedData.valid
    //    val validationErrors = ValidationErrors("helloFailure", Set.empty)
    //    NonEmptyList.of(validationErrors).invalid

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
