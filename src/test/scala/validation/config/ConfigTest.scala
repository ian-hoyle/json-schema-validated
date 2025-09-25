package validation.config

import config.ValidationConfig
import org.scalatest.funsuite.AnyFunSuite
import config.ValidationConfig.{ConfigParameters, JsonConfig, decodeConfig}

import scala.io.Source

class ConfigTest extends AnyFunSuite:
  test("Load config from Resources") {
    val jsonConfigFileName =
      "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val jsonConfigFileResources = "config.json"
    val idKey                   = "File path"
    val params                  = ConfigParameters(jsonConfigFileResources, "organisationBase.json", decodeConfig(jsonConfigFileResources))

    val propertyValueConvertor = ValidationConfig.stringValueMapper(params)

    assert(propertyValueConvertor("description_closed", "YES") == true)

    val alternateKeys = ValidationConfig.alternateKeys(params)
    assert(alternateKeys.contains("TDRMetadataUpload"))

    val domainPropertyToBaseProperty = ValidationConfig.domainPropertyToBasePropertyMapper(params)
    assert(domainPropertyToBaseProperty("TDRMetadataUpload")("Filepath") == "file_path")
    assert(domainPropertyToBaseProperty("TDRDataLoad")("FilePath") == "file_path")
  }

  test("Creates header property convertors") {
    val jsonConfigFileName = "config.json"
    val altKey             = "TDRMetadataUpload"
    val params             = ConfigParameters(jsonConfigFileName, "organisationBase.json", decodeConfig(jsonConfigFileName))

    val propertyToAlternateKey = ValidationConfig.domainBasePropertyToPropertyMapper(params)
    assert(propertyToAlternateKey("TDRMetadataUpload")("date_last_modified") == "Date last modified")

    val alternateKeyToProperty = ValidationConfig.domainPropertyToBasePropertyMapper(params)

    assert(alternateKeyToProperty("TDRMetadataUpload")("Date last modified") == "date_last_modified")

    val propertyValueConvertor = ValidationConfig.stringValueMapper(params)

    assert(propertyValueConvertor("description_closed", "YES") == true)

  }

  test("Invalid alternate key returns original value") {
    val jsonConfigFileName =
      "https://raw.githubusercontent.com/nationalarchives/da-metadata-schema/main/metadata-schema/baseSchema.schema.json"
    val altKey = "badKey"
    val params = ConfigParameters(jsonConfigFileName, "organisationBase.json", decodeConfig("config.json"))

    val propertyToAlternateKey = ValidationConfig.domainBasePropertyToPropertyMapper(params)
    assert(propertyToAlternateKey(altKey)("date_last_modified") == "date_last_modified")

    val alternateKeyToProperty = ValidationConfig.domainPropertyToBasePropertyMapper(params)

    assert(alternateKeyToProperty(altKey)("Date last modified") == "Date last modified")

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
