package validation

import cats.data.ValidatedNel
import cats.effect.IO
import cats.implicits.*
import validation.DataValidationResult
import validation.error.CSVValidationResult.dataValidationResultMonoid
import validation.error.ValidationErrors
import cats.effect.unsafe.implicits.global

object Validation {

  /**
   * Validates the provided data using a sequence of fail-fast validations followed by composed validations.
   *
   * @param dataLoader          `ValidatedNel[ValidationErrors, List[RowData]]` - The data to validate, wrapped in a DataValidationResult.
   * @param failFastValidations `Seq[List[RowData] => DataValidationResult[List[RowData]]]` - A sequence of validations to apply in order, stopping at the first failure.
   * @param composeValidations  `Seq[List[RowData] => DataValidationResult[List[RowData]]]` - A sequence of validations to apply after fail-fast validations, combining their results.
   * @return The result of applying all validations, as a ValidatedNel.
   */
  def validate(dataLoader: ValidatedNel[ValidationErrors, List[RowData]],
               failFastValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]],
               composeValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]]): DataValidationResult[List[RowData]] = {

    val failFastValidated = failFastValidations.foldLeft(dataLoader) {
      (acc, validate) => {
        acc.andThen(validate)
      }
    }

    failFastValidated.andThen(data => runComposeValidationsInParallel(composeValidations)(data))
  }

  private def runComposeValidationsInParallel(
    composeValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]])(
    data: List[RowData]
  ): DataValidationResult[List[RowData]] = {
    composeValidations.map(validation => IO(validation(data))).parSequence.map(_.combineAll).unsafeRunSync()
  }
}
