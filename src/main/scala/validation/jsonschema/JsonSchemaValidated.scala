package validation.jsonschema

import cats.data.Validated.*
import cats.data.{NonEmptyList, Validated}
import cats.effect.IO
import cats.implicits.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import validation.RowData
import validation.error.CSVValidationResult.combineCSVValidationResult
import validation.error.ValidationErrors
import validation.jsonschema.ValidatedSchema.CSVValidationResult


object JsonSchemaValidated:

  def validateWithSchema(fileValidation: CSVValidationResult[List[RowData]], schema: String): IO[CSVValidationResult[List[RowData]]] = {
    fileValidation match {
      case Valid(value) => IO(ValidatedSchema.schemaValidated(Some(schema))(value))
      case Invalid(errors) => IO.pure(errors.invalid)
    }
  }

  def validateWithMultipleSchema(fileValidation: CSVValidationResult[List[RowData]], schema: List[String]): IO[CSVValidationResult[List[RowData]]] = {
    fileValidation match {
      case Valid(value) =>
        val schemaValidations = schema.map(x => ValidatedSchema.schemaValidated(Some(x)))
        val dataValidations = schemaValidations.map { validation =>
          IO(validation(value))
        }
        dataValidations.parSequence.map(_.combineAll)
      case Invalid(errors) =>
        IO.pure(errors.invalid)
    }
  }


  def addJsonValidated(keyMapper: String => String, valueMapper: (String,String) => Any)(data: List[RowData]): CSVValidationResult[List[RowData]] = {
    val validatedData = data.map { row =>
      val json = convertToJSONString(row.data, keyMapper, valueMapper)
      row.copy(json = Some(json))
    }
    validatedData.valid
  }

  private def convertToJSONString(data: Map[String, Any], keyMapper: String => String, valueMapper: (String, String) => Any) = {
    val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val convertedData = data.map {
      case (header, value: String) =>
        val property = keyMapper(header)
        (property,
          if (value.isEmpty) null
          else valueMapper(property,value)
        )
      case (header, value) =>
        val property = keyMapper(header)
        (property, value)
    }
    val generatedJson = mapper.writeValueAsString(convertedData)
    generatedJson
  }


