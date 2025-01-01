package validation

import org.scalatest.funsuite.AnyFunSuite
import cats.data.Validated.*

class CSVValidatorTest extends AnyFunSuite:
  test("Validate a csv where a row fails schema validation") {
    val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val altKey = "tdrFileHeader"
    val idKey = "File path"
    val params = Parameters(jsonConfigFileName, List("","","ian"), Some(altKey), "sample.csv", Some(idKey),Some("ian"))

    val pp = CSVValidator.prepareCSVConfiguration(params)
    import cats.effect.unsafe.implicits.*
    val runMe = CSVValidator.validationProgram(params).unsafeRunSync()
    runMe match
      case Valid(data) => fail("Should have failed")
      case Invalid(error) => assert(error.toList.size == 1)

  }
