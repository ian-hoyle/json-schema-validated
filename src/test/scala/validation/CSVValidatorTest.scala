package validation

import cats.data.Validated.*
import org.scalatest.funsuite.AnyFunSuite


class CSVValidatorTest extends AnyFunSuite:
  test("Validate a csv where a row fails schema validation") {
    //val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val jsonConfigFileName = "organisationBase.json"
    val altKey = "tdrFileHeader"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, List(jsonConfigFileName, "openRecord.json"), Some(altKey), "sample.csv", Some(idKey), Some(jsonConfigFileName),Some(altKey))

    import cats.effect.unsafe.implicits.*
    val runMe = CSVFileValidationLambdaHandler.csvFileValidation(params).unsafeRunSync()
    runMe match
      case Valid(data) => println(data)
        fail("Should have failed")
      case Invalid(error) =>
        assert(error.toList.size == 2)

  }
