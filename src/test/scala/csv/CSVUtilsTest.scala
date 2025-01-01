package csv

import config.CSVParserConfig
import org.scalatest.funsuite.AnyFunSuite
import validation.jsonschema.ValidatedSchema.CSVValidationResult
import validation.{CSVConfiguration, CSVValidator, Parameters}

class CSVUtilsTest extends AnyFunSuite:
  test("Load schema") {
    val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val altKey = "tdrFileHeader"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, List.empty[String], Some(altKey), "sample.csv", Some(idKey))

    val csvValidationConfig: CSVValidationResult[CSVConfiguration] = CSVValidator.prepareCSVConfiguration(params)

    val csvMap = csvValidationConfig andThen CSVUtils.loadCSVData
    println(csvMap)
    assert(true)

  }
