package validation.jsonschema

import cats.implicits.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import validation.{DataValidationResult, RowData}

object ValidationDataUtils:

  def mapKeys(
      keyMapper: String => String
  )(inputData: List[RowData]): DataValidationResult[List[RowData]] = {
    val validatedData = inputData.map { row =>
      row.copy(data = row.data.map { case (k, v) => keyMapper(k) -> v })
    }
    validatedData.valid
  }

  def addJsonForValidation(
      valueMapper: (String, String) => Any
  )(data: List[RowData]): DataValidationResult[List[RowData]] = {
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
        (
          header,
          if (value.isEmpty) null
          else valueMapper(header, value)
        )
      case (header, value) =>
        (header, value)
    }
    mapper.writeValueAsString(convertedData)
  }
