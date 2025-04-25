package validation.config

import cats.data.Reader
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

  def prepareValidationConfiguration(configFile: String, alternateKey: Option[String]): ValidatorConfiguration = {

      val csvConfigurationReader = for {
        altHeaderToPropertyMapper <- Reader(ValidationConfig.domainKeyToPropertyMapper)
        propertyToAltHeaderMapper <- Reader(ValidationConfig.propertyToDomainKeyMapper)
        valueMapper <- Reader(ValidationConfig.stringValueMapper)
      } yield ValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper,
        valueMapper)
      csvConfigurationReader.run(ConfigParameters(configFile, alternateKey, "organisationBase.json", decodeConfig(configFile)))
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

  private def loadProperties(file: String): LinkedHashMap[String, Value] = {
    val data = loadData(file)
    val json = ujson.read(data.getOrElse(""))
    val jsonMap: LinkedHashMap[String, Value] = json("properties").obj
    jsonMap
  }

  private def getPropertyType(propertyValue: ujson.Obj): String = {
    propertyValue.obj.get("type") match {
      case Some(ujson.Str(singleType)) => singleType
      case Some(ujson.Arr(types)) if types.nonEmpty =>
        types.find(a => a.isInstanceOf[ujson.Str] && a.str != "null").map(_.str).getOrElse("unknown")
      case _ => "unknown"
    }
  }

  private def convertValueFunction(propertyType: String): String => Any = {
    propertyType match {
      case "integer" => (str: String) => Try(str.toInt).getOrElse(str)
      case "array" => (str: String) => if (str.isEmpty) "" else str.split("\\|")
      case "boolean" =>
        (str: String) =>
          str.toUpperCase match {
            case "YES" | "TRUE" => true
            case "NO" | "FALSE" => false
            case _ => str
          }
      case _ => (str: String) => str
    }
  }
