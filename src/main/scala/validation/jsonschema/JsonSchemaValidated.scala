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
import validation.jsonschema.ValidatedSchema.DataValidationResult


object JsonSchemaValidated:

  def validateWithSchema(fileValidation: DataValidationResult[List[RowData]], schema: String): IO[DataValidationResult[List[RowData]]] = {
    fileValidation match {
      case Valid(value) => IO(ValidatedSchema.schemaValidated(Some(schema))(value))
      case Invalid(errors) => IO.pure(errors.invalid)
    }
  }

  def validateWithMultipleSchema(fileValidation: DataValidationResult[List[RowData]], schema: List[String],propertyToAll:String=>String): IO[DataValidationResult[List[RowData]]] = {
    fileValidation match {
      case Valid(value) =>
        val schemaValidations = schema.map(x => ValidatedSchema.schemaValidated(Some(x), true, propertyToAll))
        val dataValidations = schemaValidations.map { validation =>
          IO(validation(value))
        }
        dataValidations.parSequence.map(_.combineAll)
      case Invalid(errors) =>
        IO.pure(errors.invalid)
    }
  }


  def addJsonToData(keyMapper: String => String, valueMapper: (String,String) => Any)(data: List[RowData]): DataValidationResult[List[RowData]] = {
    val validatedData = data.map { row =>
      val json = convertToJSONString(row.data, keyMapper, valueMapper)
      row.copy(json = Some(json))
    }
    validatedData.valid
    //    val validationErrors = ValidationErrors("helloFailure", Set.empty)
    //    NonEmptyList.of(validationErrors).invalid

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


