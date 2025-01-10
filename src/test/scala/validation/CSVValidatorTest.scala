package validation

import org.scalatest.funsuite.AnyFunSuite
import cats.data.Validated.*
import validation.CSVFileValidationLambdaHandler.csvFileValidation

class CSVValidatorTest extends AnyFunSuite:
  test("Validate a csv where a row fails schema validation") {
    //val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val jsonConfigFileName = "DaBase.json"
    val altKey = "tdrFileHeader"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, List(jsonConfigFileName,jsonConfigFileName), Some(altKey), "sample.csv", Some(idKey),Some(jsonConfigFileName))

    import cats.effect.unsafe.implicits.*
    val runMe = csvFileValidation(params).unsafeRunSync()
    runMe match
      case Valid(data) => println(data)
        fail("Should have failed")
      case Invalid(error) =>
        assert(error.toList.size == 1)

  }
