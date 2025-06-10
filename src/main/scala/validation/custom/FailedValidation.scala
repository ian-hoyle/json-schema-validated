package validation.custom

import cats.data.NonEmptyList
import cats.syntax.validated.*
import validation.error.{JsonSchemaValidationError, ValidationErrors}
import validation.{DataValidationResult, RowData}

object FailedValidation {
  def failedValidation(
      data: List[RowData]
  ): DataValidationResult[List[RowData]] = {
    val error =  JsonSchemaValidationError("forced failure", "No property", "Force", "hello world failure", "Lets just fail")
    val validationErrors = ValidationErrors("hello world", Set(error))
    NonEmptyList.of(validationErrors).invalid
  }
}
