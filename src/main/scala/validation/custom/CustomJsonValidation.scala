package validation.custom

import cats.data.NonEmptyList
import cats.syntax.validated.*
import validation.error.{JsonSchemaValidationError, ValidationErrors}
import validation.{Data, DataValidation}

object CustomJsonValidation {
  def validateClosureFields(altInToKey: String => String): List[Data] => DataValidation = { data =>
    val errors = data.foldLeft(List.empty[ValidationErrors]) { (errAcc, dataItem) =>
      dataItem.json.fold(errAcc) { jsonString =>
        ClosureFields
          .fromJson(jsonString)
          .fold(
            _ => errAcc, // On parsing error, don't add to errors
            fields => {
              if (fields.closureType == Some("Closed") && fields.closurePeriod.size != fields.foiExemptionCode.size) {
                val assetId = dataItem.assetId.getOrElse("unknown")

                val closurePeriodError = closureFieldsMismatchError(
                  property = altInToKey("closure_period"),
                  originalValue = fields.closurePeriod.mkString("[", ", ", "]")
                )

                val foiExemptionError = closureFieldsMismatchError(
                  property = altInToKey("foi_exemption_code"),
                  originalValue = fields.foiExemptionCode.mkString("[", ", ", "]")
                )

                val validationError = ValidationErrors(
                  assetId = assetId,
                  errors = Set(closurePeriodError, foiExemptionError)
                )

                errAcc :+ validationError
              } else {
                errAcc
              }
            }
          )
      }
    }

    NonEmptyList.fromList(errors) match {
      case Some(nel) => nel.invalid
      case None      => data.valid
    }
  }

  private def closureFieldsMismatchError(property: String, originalValue: String): JsonSchemaValidationError = JsonSchemaValidationError(
    validationProcess = "ClosedRecordValidation",
    property = property,
    errorKey = "SIZE_MISMATCH",
    message = "Closure period and FOI exemption code arrays must have the same size.",
    value = originalValue
  )
}
