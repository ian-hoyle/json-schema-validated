package validation.config

import ConfigUtils.{convertValueFunction, getPropertyType, loadProperties}
import cats.data.Reader
import cats.effect.IO
import ujson.{Arr, Value}
import upickle.core.LinkedHashMap
import validation.{ConfigItem, ConfigParameters, JsonConfig, ValidatorConfiguration}

import scala.collection.mutable
import io.circe.generic.auto.*
import io.circe.parser.decode
import cats.implicits.*
import validation.jsonschema.ValidatedSchema.loadData

import scala.util.{Failure, Success, Try}

object ValidationConfig:

  def prepareValidationConfiguration(configFile: String, alternateKey: Option[String]): IO[ValidatorConfiguration] = {
    IO({
      val csvConfigurationReader = for {
        altHeaderToPropertyMapper <- Reader(ValidationConfig.domainKeyToPropertyMapper)
        propertyToAltHeaderMapper <- Reader(ValidationConfig.propertyToDomainKeyMapper)
        valueMapper <- Reader(ValidationConfig.stringValueMapper)
      } yield ValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper,
        valueMapper)
      csvConfigurationReader.run(ConfigParameters(configFile, alternateKey, "organisationBase.json", decodeConfig(configFile)))
    }
    ) //TODO handle error with raiseError that contains ValidationResult
  }

  def domainKeyToPropertyMapper(parameters: ConfigParameters): String => String =
    val configMap: Map[String, String] = parameters.jsonConfig.configItems.foldLeft(Map[String, String]())((acc, item) => {
      item.domainKeys match
        case Some(domainKeys) => domainKeys.find(x => Option(x.domain) === parameters.alternates) match
          case Some(domainKey) => acc + (domainKey.domainKey -> item.key)
          case None => acc + (item.key -> item.key)
        case None => acc + (item.key -> item.key)
    })
    (x: String) => configMap.getOrElse(x, x)

  def propertyToDomainKeyMapper(parameters: ConfigParameters): String => String =
    val configMap: Map[String, String] = parameters.jsonConfig.configItems.foldLeft(Map[String, String]())((acc, item) => {
      item.domainKeys match
        case Some(domainKeys) => domainKeys.find(x => Option(x.domain) === parameters.alternates) match
          case Some(domainKey) => acc + (item.key -> domainKey.domainKey)
          case None => acc + (item.key -> item.key)
        case None => acc + (item.key -> item.key)
    })
    (x: String) => configMap.getOrElse(x, x)

  def stringValueMapper(parameters: ConfigParameters): (property: String, value: String) => Any = {
    val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.baseSchema)
    val propertyToValueConversionMap: mutable.Map[String, String => Any] = jsonMap.map { case (k, v) => k -> convertValueFunction(getPropertyType(v.obj)) }
    (property: String, value: String) => propertyToValueConversionMap.getOrElse(property, (x: Any) => x)(value)
  }


  def decodeConfig(csConfig: String): JsonConfig = {
    val configFile: Try[String] = loadData(csConfig)
    val configData = configFile match
      case Success(data) => decode[JsonConfig](data).getOrElse(JsonConfig(List.empty[ConfigItem]))
      case Failure(exception) => JsonConfig(configItems = List.empty[ConfigItem])
    configData
  }

  private def getAlternate(alternate: String, a: String, arr: Arr) = {
    arr.value.head.obj.get(alternate) match
      case Some(v) => v.str
      case _ => a
  }
