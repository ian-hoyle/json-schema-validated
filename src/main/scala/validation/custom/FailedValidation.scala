package validation.custom

import cats.data.NonEmptyList
import cats.syntax.validated.*
import validation.error.{JsonSchemaValidationError, ValidationErrors}
import validation.{DataValidation, Data}

object FailedValidation {
  def failedValidation(
      data: List[Data]
  ): DataValidation = {
    val error = JsonSchemaValidationError(
      validationProcess = "forced failure",
      property = "No property",
      errorKey = "Force",
      message = "hello world failure",
      value = "Lets just fail"
    )

    val validationErrors = ValidationErrors("hello world", Set(error))
    NonEmptyList.of(validationErrors).invalid
  }
}
