package config

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import config.CSVConfig.loadResourceFile
import org.scalatest.funsuite.AnyFunSuite

class CSVUtilsTest extends AnyFunSuite:
  test("Load schema") {
    val jsonFileName = "DaBase.json" // This should match your file name in resources
    val schemaNode: JsonNode = new ObjectMapper().readTree(loadResourceFile(jsonFileName))

    assert(CSVUtils.createValueConversionMap(ujson.read(loadResourceFile(jsonFileName)))("description")("you") === "you")
  }
