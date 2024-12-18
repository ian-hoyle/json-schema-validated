package config

import config.CSVConfig.loadResourceFile
import org.scalatest.funsuite.AnyFunSuite

class CSVUtilsTest extends AnyFunSuite:
  test("Load schema") {
    val jsonFileName = "DaBase.json" // This should match your file name in resources
    assert(CSVUtils.getConversionFunction("description")("you") === "you")
    assert(CSVUtils.getConversionFunction("banana")("you") === "you")
    assert(ConfigUtils.createValueConversionMap(ujson.read(loadResourceFile(jsonFileName)))("description")("you") === "you")
  }
