package config


import org.scalatest.funsuite.AnyFunSuite

class ConfigTest extends AnyFunSuite:
  test("Load schema") {
    val jsonFileName = "DaBase.json" // This should match your file name in resources
    val config =CSVConfig.createConfig(jsonFileName)
    assert(config.valueMap("description")("you") === "you")
  }
