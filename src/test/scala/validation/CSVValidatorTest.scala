package validation

import org.scalatest.funsuite.AnyFunSuite
import cats.data.Validated.*

class CSVValidatorTest extends AnyFunSuite:
  test("Validate a csv where a row fails schema validation") {
    //val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val jsonConfigFileName = "DaBase.json"
    val altKey = "tdrFileHeader"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, List(jsonConfigFileName,jsonConfigFileName), Some(altKey), "sample.csv", Some(idKey),Some(jsonConfigFileName))

    import cats.effect.unsafe.implicits.*
    val runMe = CSVValidator.validationProgram(params).unsafeRunSync()
    runMe match
      case Valid(data) => println(data)
        fail("Should have failed")
      case Invalid(error) =>
        println(error)
        assert(error.toList.size == 1)

  }
