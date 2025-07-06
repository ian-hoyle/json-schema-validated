package validation.error

import cats.data.Validated.*
import cats.data.{NonEmptyList, Validated}
import cats.kernel.Monoid
import validation.error.ValidationErrors.combineValidationErrors
import validation.{DataValidation, Data}

import java.text.SimpleDateFormat
import java.util.{Date, UUID}

object FileError extends Enumeration {
  type FileError = Value
  val UTF_8, INVALID_CSV, ROW_VALIDATION, SCHEMA_REQUIRED, DUPLICATE_HEADER, SCHEMA_VALIDATION, UNKNOWN, None = Value
}

case class JsonSchemaValidationError(
    validationProcess: String,
    property: String,
    errorKey: String,
    message: String,
    value: String = ""
)

case class ValidationErrors(assetId: String, errors: Set[JsonSchemaValidationError])

object ValidationErrors {
  implicit val combineValidationErrors: Monoid[List[ValidationErrors]] =
    new Monoid[List[ValidationErrors]]:
      override def empty: List[ValidationErrors] = List.empty[ValidationErrors]

      override def combine(
          validationErrors: List[ValidationErrors],
          moreValidationErrors: List[ValidationErrors]
      ): List[ValidationErrors] =
        (validationErrors ++ moreValidationErrors)
          .groupBy(_.assetId)
          .map { case (id, validationErrors) =>
            ValidationErrors(
              assetId = id,
              errors = validationErrors.flatMap(_.errors).toSet
            )
          }
          .toList
}

import cats.implicits.*

object CSVValidationResult {
  implicit val dataValidationResultMonoid: Monoid[DataValidation] =
    new Monoid[DataValidation] {
      override def empty: DataValidation =
        Validated.valid(List.empty[Data]) // Empty list of Data is the valid default

      override def combine(
          x: DataValidation,
          y: DataValidation
      ): DataValidation =
        (x, y) match {
          case (Valid(valueX), Valid(valueY)) => Valid(valueY)
          case (Invalid(errorsX), Invalid(errorsY)) =>
            Invalid(NonEmptyList.fromList(errorsX.toList |+| errorsY.toList).get)
          case (Valid(valueX), Invalid(errorsY)) => Invalid(errorsY)
          case (Invalid(errorsX), Valid(valueY)) => Invalid(errorsX)
        }
    }
}

case class ErrorFileData(
    consignmentId: UUID,
    date: String,
    validationErrors: List[ValidationErrors]
)

object ErrorFileData {

  def apply(validationErrors: List[ValidationErrors] = Nil): ErrorFileData = {

    val pattern    = "yyyy-MM-dd"
    val dateFormat = new SimpleDateFormat(pattern)
    ErrorFileData(UUID.randomUUID(), dateFormat.format(new Date), validationErrors)
  }
}
