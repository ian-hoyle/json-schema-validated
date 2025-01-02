package csv

import cats.data.{NonEmptyList, Validated}
import config.CSVParserConfig
import error.ValidationErrors
import org.scalatest.funsuite.AnyFunSuite
import validation.jsonschema.ValidatedSchema.CSVValidationResult
import validation.{CSVConfiguration, CSVValidator, Parameters}
import cats.data.Validated.*

class CSVUtilsTest extends AnyFunSuite:
  test("Load schema") {
    val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val altKey = "tdrFileHeader"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, List.empty[String], Some(altKey), "sample.csv", Some(idKey))

    val csvValidationConfig: CSVValidationResult[CSVConfiguration] = CSVValidator.prepareCSVConfiguration(params)

    val csvMap: Validated[NonEmptyList[ValidationErrors], List[RowData]] = csvValidationConfig andThen CSVUtils.loadCSVData

    csvMap match {
      case Valid(data) => assert(data.nonEmpty)
      case Invalid(error) => fail("failed to load")
    }
  }
