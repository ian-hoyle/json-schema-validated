package validation.error

import cats.data.Validated.*
import cats.data.{NonEmptyList, Validated}
import cats.kernel.Monoid
import FileError.FileError
import validation.RowData
import validation.jsonschema.ValidatedSchema.DataValidationResult

import java.text.SimpleDateFormat
import java.util.{Date, UUID}

object FileError extends Enumeration {
  type FileError = Value
  val UTF_8, INVALID_CSV, ROW_VALIDATION, SCHEMA_REQUIRED, DUPLICATE_HEADER, SCHEMA_VALIDATION, UNKNOWN, None = Value
}

case class Metadata(a: String)

case class JsonSchemaValidationError(validationProcess: String, property: String, errorKey: String, message: String, value: String = "")

case class ValidationErrors(assetId: String, errors: Set[JsonSchemaValidationError])

object ValidationErrors {
  implicit val combineValidationErrors: Monoid[List[ValidationErrors]] = new Monoid[List[ValidationErrors]]:
    override def empty: List[ValidationErrors] = List.empty[ValidationErrors]

    override def combine(validationErrors: List[ValidationErrors], moreValidationErrors: List[ValidationErrors]): List[ValidationErrors] =
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
  implicit val combineCSVValidationResult: Monoid[DataValidationResult[List[RowData]]] = new Monoid[DataValidationResult[List[RowData]]] {
    override def empty: DataValidationResult[List[RowData]] =
      Validated.valid(List.empty[RowData]) // Empty list of RowData is the valid default

    override def combine(x: DataValidationResult[List[RowData]], y: DataValidationResult[List[RowData]] ): DataValidationResult[List[RowData]] =
      (x, y) match {
        case (Valid(valueX), Valid(valueY)) =>
             Valid(valueY)
        case (Invalid(errorsX), Invalid(errorsY)) =>
          import ValidationErrors.combineValidationErrors
          Invalid(NonEmptyList.fromList(errorsX.toList |+| errorsY.toList).get)
        case (Valid(valueX), Invalid(errorsY)) =>
          Invalid(errorsY)
        case (Invalid(errorsX), Valid(valueY)) =>
          Invalid(errorsX)
      }
  }
}


case class ErrorFileData(consignmentId: UUID, date: String, fileError: FileError, validationErrors: List[ValidationErrors])

object ErrorFileData {

  def apply(fileError: FileError = FileError.None, validationErrors: List[ValidationErrors] = Nil): ErrorFileData = {

    val pattern = "yyyy-MM-dd"
    val dateFormat = new SimpleDateFormat(pattern)
    ErrorFileData(UUID.randomUUID(), dateFormat.format(new Date), fileError, validationErrors)
  }
}
