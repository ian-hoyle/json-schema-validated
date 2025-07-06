package validation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import validation.DataValidation
import validation.error.CSVValidationResult.dataValidationResultMonoid

object Validation {

  /** Validates the provided data using a sequence of fail-fast validations followed by composed validations.
    *
    * @param dataLoader
    *   `ValidatedNel[ValidationErrors, List[Data]]` - The data to validate, wrapped in a DataValidationResult.
    * @param failFastValidations
    *   `Seq[List[Data] => DataValidation]` - A sequence of validations to apply in order, stopping at the first failure.
    * @param composeValidations
    *   `Seq[List[Data] => DataValidation]` - A sequence of validations to apply after fail-fast validations, combining their results.
    * @return
    *   The result of applying all validations, as a ValidatedNel.
    */
  def validate(
                dataLoader: DataValidation,
                failFastValidations: Seq[List[Data] => DataValidation],
                composeValidations: Seq[List[Data] => DataValidation]
  ): DataValidation = {

    val failFastValidated = failFastValidations.foldLeft(dataLoader) { (acc, validate) =>
      {
        acc.andThen(validate)
      }
    }

    failFastValidated.andThen(data => runComposeValidationsInParallel(composeValidations)(data))
  }

  private def runComposeValidationsInParallel(
      composeValidations: Seq[List[Data] => DataValidation]
  )(
      data: List[Data]
  ): DataValidation = {
    composeValidations
      .map(validation => IO(validation(data)))
      .parSequence
      .map(_.combineAll)
      .unsafeRunSync()
  }
}
