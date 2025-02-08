package validation.config

import org.scalatest.funsuite.AnyFunSuite
import validation.{ConfigParameters, Parameters}

class ConfigTest extends AnyFunSuite:
  test("Load config from Resources") {
    val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val jsonConfigFileResources = "organisationBase.json"
    val altKey = "tdrFileHeader"
    val idKey = "File path"
    val params = ConfigParameters(jsonConfigFileResources, Some(altKey))

    val propertyToAlternateKey = ValidationConfig.propertyToInAlternateKeyMapper(params)
    assert(propertyToAlternateKey("date_last_modified") == "Date last modified")

    val alternateKeyToProperty = ValidationConfig.alternateKeyToPropertyMapper(params)

    assert(alternateKeyToProperty("Date last modified") == "date_last_modified")

    val propertyValueConvertor = ValidationConfig.stringValueMapper(params)

    assert(propertyValueConvertor("description_closed","YES") == true)

  }
  test("Creates header property convertors") {
    val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val altKey = "tdrFileHeader"
    val params = ConfigParameters(jsonConfigFileName, Some(altKey))

    val propertyToAlternateKey = ValidationConfig.propertyToInAlternateKeyMapper(params)
    assert(propertyToAlternateKey("date_last_modified") == "Date last modified")

    val alternateKeyToProperty = ValidationConfig.alternateKeyToPropertyMapper(params)

    assert(alternateKeyToProperty("Date last modified") == "date_last_modified")

    val propertyValueConvertor = ValidationConfig.stringValueMapper(params)

    assert(propertyValueConvertor("description_closed","YES") == true)

  }

  test("Invalid alternate key returns original value") {
    val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val altKey = "badKey"
    val params = ConfigParameters(jsonConfigFileName, Some(altKey))

    val propertyToAlternateKey = ValidationConfig.propertyToInAlternateKeyMapper(params)
    assert(propertyToAlternateKey("date_last_modified") == "date_last_modified")

    val alternateKeyToProperty = ValidationConfig.alternateKeyToPropertyMapper(params)

    assert(alternateKeyToProperty("Date last modified") == "Date last modified")

    val propertyValueConvertor = ValidationConfig.stringValueMapper(params)

    assert(propertyValueConvertor("description_closed","YES") == true)

  }
