package validation

import cats.data.Validated.*
import examples.CSVFileValidationLambdaHandler
import org.scalatest.funsuite.AnyFunSuite


class CSVLambdaHandlerValidatorTest extends AnyFunSuite:
  test("Validate a csv where a row fails schema validation") {
    //val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val jsonConfigFileName = "config.json"
    val baseFile = "organisationBase.json"

    val altKey = "TDRMetadataUpload"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, baseFile, List(baseFile, "openRecord.json"), Some(altKey), "sample.csv", Some(idKey), Some(baseFile), Some(altKey))

    import cats.effect.unsafe.implicits.*
    val runMe = CSVFileValidationLambdaHandler.csvFileValidation(params).unsafeRunSync()
    runMe match
      case Valid(data) => fail("Should have failed")
      case Invalid(error) =>
        assert(error.head.assetId equals "test/test2.txt")
        assert(error.head.errors.toList.head.errorKey equals "enum")
        assert(error.head.errors.toList.head.value equals "OpenX")
        assert(error.head.errors.toList.head.property equals "Closure status")
        assert(error.toList.size == 2)

  }
