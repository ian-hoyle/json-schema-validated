package validation.config

import config.ValidationConfig
import org.scalatest.funsuite.AnyFunSuite
import config.ValidationConfig.decodeConfig
import validation.{ConfigParameters, JsonConfig}

import scala.io.Source

class ConfigTest extends AnyFunSuite:
  test("Load config from Resources") {
    val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val jsonConfigFileResources = "config.json"
    val altKey = "TDRMetadataUpload"
    val idKey = "File path"
    val params = ConfigParameters(jsonConfigFileResources, Some(altKey), "organisationBase.json", decodeConfig(jsonConfigFileResources))

    val propertyToAlternateKey = ValidationConfig.propertyToDomainKeyMapper(params)
    assert(propertyToAlternateKey("date_last_modified") == "Date last modified")

    val alternateKeyToProperty = ValidationConfig.domainKeyToPropertyMapper(params)

    assert(alternateKeyToProperty("Date last modified") == "date_last_modified")

    val propertyValueConvertor = ValidationConfig.stringValueMapper(params)

    assert(propertyValueConvertor("description_closed", "YES") == true)

  }
  test("Creates header property convertors") {
    val jsonConfigFileName = "config.json"
    val altKey = "TDRMetadataUpload"
    val params = ConfigParameters(jsonConfigFileName, Some(altKey), "organisationBase.json", decodeConfig(jsonConfigFileName))

    val propertyToAlternateKey = ValidationConfig.propertyToDomainKeyMapper(params)
    assert(propertyToAlternateKey("date_last_modified") == "Date last modified")

    val alternateKeyToProperty = ValidationConfig.domainKeyToPropertyMapper(params)

    assert(alternateKeyToProperty("Date last modified") == "date_last_modified")

    val propertyValueConvertor = ValidationConfig.stringValueMapper(params)

    assert(propertyValueConvertor("description_closed", "YES") == true)

  }

  test("Invalid alternate key returns original value") {
    val jsonConfigFileName = "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val altKey = "badKey"
    val params = ConfigParameters(jsonConfigFileName, Some(altKey), "organisationBase.json", decodeConfig("config.json"))

    val propertyToAlternateKey = ValidationConfig.propertyToDomainKeyMapper(params)
    assert(propertyToAlternateKey("date_last_modified") == "date_last_modified")

    val alternateKeyToProperty = ValidationConfig.domainKeyToPropertyMapper(params)

    assert(alternateKeyToProperty("Date last modified") == "Date last modified")

    val propertyValueConvertor = ValidationConfig.stringValueMapper(params)

    assert(propertyValueConvertor("description_closed", "YES") == true)

  }

  test("I can load a config from a file") {
    val jsonConfigFileName = "config.json"

    import io.circe.generic.auto.*
    import io.circe.parser.decode

    val data = Source.fromResource(jsonConfigFileName).getLines().mkString("\n")
    decode[JsonConfig](data) match {
      case Right(config) =>
        assert(config.configItems.head.key == "file_path")
      case Left(error) => fail(s"Failed to parse config: ${error.getMessage}")
    }

  }
