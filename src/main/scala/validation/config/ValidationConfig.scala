package validation.config

import ConfigUtils.{convertValueFunction, getPropertyType, loadProperties}
import cats.data.Reader
import cats.effect.IO
import ujson.{Arr, Value}
import upickle.core.LinkedHashMap
import validation.{ConfigItem, ConfigParameters, JsonConfig, Parameters, ValidatorConfiguration}

import scala.collection.mutable
import validation.jsonschema.loadData
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*

import scala.util.{Failure, Success, Try}
object ValidationConfig:

  def domainKeyToPropertyMapper(parameters: ConfigParameters): String => String =
    val configData: JsonConfig = decodeConfig(parameters.csConfig)

    val configMap: Map[String, String] = configData.configItems.foldLeft(Map[String,String]())((acc, item) => {
      item.domainKeys match
        case Some(domainKeys) => domainKeys.find(_.domain == parameters.alternates.getOrElse("")) match
          case Some(domainKey) => acc + (domainKey.domainKey -> item.key)
          case None => acc + (item.key -> item.key)
        case None => acc + (item.key -> item.key)
    })

    (x: String) => configMap.getOrElse(x, x)

  def propertyToDomainKeyMapper(parameters: ConfigParameters): String => String =
    val configData: JsonConfig = decodeConfig(parameters.csConfig)

    val configMap: Map[String, String] = configData.configItems.foldLeft(Map[String,String]())((acc, item) => {
      item.domainKeys match
        case Some(domainKeys) => domainKeys.find(_.domain == parameters.alternates.getOrElse("")) match
          case Some(domainKey) => acc + (item.key -> domainKey.domainKey)
          case None => acc + (item.key -> item.key)
        case None => acc + (item.key -> item.key)
    })

    (x: String) => configMap.getOrElse(x, x)

  def stringValueMapper(parameters: ConfigParameters):(property: String,value: String) => Any = {
    val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.baseSchema)
    val propertyToValueConversionMap: mutable.Map[String, String => Any] = jsonMap.map { case (k, v) => k -> convertValueFunction(getPropertyType(v.obj)) }
    (property: String, value: String) => propertyToValueConversionMap.getOrElse(property, (x:Any) => x)(value)
  }


  private def decodeConfig(csConfig: String) = {
    val configFile: Try[String] = loadData(csConfig)
    val config = configFile.map(decode[JsonConfig])

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

  def prepareValidationConfiguration(configFile: String, alternateKey: Option[String]): IO[ValidatorConfiguration] = {
    IO({
      val csvConfigurationReader = for {
        altHeaderToPropertyMapper <- Reader(ValidationConfig.domainKeyToPropertyMapper)
        propertyToAltHeaderMapper <- Reader(ValidationConfig.propertyToDomainKeyMapper)
        valueMapper <- Reader(ValidationConfig.stringValueMapper)
      } yield ValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper,
        valueMapper)
      csvConfigurationReader.run(ConfigParameters(configFile, alternateKey, "organisationBase.json"))
    }
    ) //TODO handle error with raiseError that contains ValidationResult
  }









