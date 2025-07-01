package validation.custom

import cats.data.NonEmptyList
import cats.syntax.validated.*
import validation.error.{JsonSchemaValidationError, ValidationErrors}
import validation.{DataValidation, RowData}

object FailedValidation {
  def failedValidation(
      data: List[RowData]
  ): DataValidation = {
    val error = JsonSchemaValidationError(
      validationProcess = "forced failure",
      property = "No property",
      errorKey = "Force",
      message = "hello world failure",
      "Lets just fail"
    )

    val validationErrors = ValidationErrors("hello world", Set(error))
    NonEmptyList.of(validationErrors).invalid
  }
}
