package config

import cats.data.Reader
import ujson.{Arr, Obj, Value}
import upickle.core.LinkedHashMap
import validation.ValidatorConfiguration

import scala.collection.mutable
import io.circe.generic.auto.*
import io.circe.parser.decode
import cats.implicits.*
import validation.jsonschema.ValidatedSchema.loadData

import scala.util.{Failure, Success, Try}

object ValidationConfig:

  private val ARRAY_SPLIT_CHAR = '|'

  case class ConfigParameters(
      csConfig: String,
      alternates: Option[String],
      baseSchema: String,
      jsonConfig: JsonConfig
  )

  case class JsonConfig(configItems: List[ConfigItem])

  case class ConfigItem(key: String, domainKeys: Option[List[DomainKey]])

  case class DomainKey(domain: String, domainKey: String)

  case class DomainValidation(domain: String, domainValidation: String)

  def prepareValidationConfiguration(
      configFile: String,
      baseSchema: String,
      alternateKey: Option[String]
  ): ValidatorConfiguration = {

    val csvConfigurationReader = for {
      altHeaderToPropertyMapper <- Reader(ValidationConfig.domainKeyToPropertyMapper)
      propertyToAltHeaderMapper <- Reader(ValidationConfig.propertyToDomainKeyMapper)
      valueMapper               <- Reader(ValidationConfig.stringValueMapper)
    } yield ValidatorConfiguration(
      altHeaderToPropertyMapper,
      propertyToAltHeaderMapper,
      valueMapper
    )
    csvConfigurationReader.run(
      ConfigParameters(configFile, alternateKey, baseSchema, decodeConfig(configFile))
    )
  }

  def domainKeyToPropertyMapper(parameters: ConfigParameters): String => String =
    val configMap: Map[String, String] =
      parameters.jsonConfig.configItems.foldLeft(Map[String, String]())((acc, item) => {
        item.domainKeys match
          case Some(domainKeys) =>
            domainKeys.find(x => Option(x.domain) === parameters.alternates) match
              case Some(domainKey) => acc + (domainKey.domainKey -> item.key)
              case None            => acc + (item.key            -> item.key)
          case None => acc + (item.key -> item.key)
      })
    (x: String) => configMap.getOrElse(x, x)

  def propertyToDomainKeyMapper(parameters: ConfigParameters): String => String =
    val configMap: Map[String, String] =
      parameters.jsonConfig.configItems.foldLeft(Map[String, String]())((acc, item) => {
        item.domainKeys match
          case Some(domainKeys) =>
            domainKeys.find(x => Option(x.domain) === parameters.alternates) match
              case Some(domainKey) => acc + (item.key -> domainKey.domainKey)
              case None            => acc + (item.key -> item.key)
          case None => acc + (item.key -> item.key)
      })
    (x: String) => configMap.getOrElse(x, x)

  def stringValueMapper(parameters: ConfigParameters): (property: String, value: String) => Any = {
    val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.baseSchema)
    val propertyToValueConversionMap: mutable.Map[String, String => Any] = jsonMap.map { case (k, v) =>
      k -> convertValueFunction(getPropertyType(v.obj))
    }
    (property: String, value: String) => propertyToValueConversionMap.getOrElse(property, (x: Any) => x)(value)
  }

  def decodeConfig(csConfig: String): JsonConfig = {
    val configFile: Try[String] = loadData(csConfig)
    val configData = configFile match
      case Success(data)      => decode[JsonConfig](data).getOrElse(JsonConfig(List.empty[ConfigItem]))
      case Failure(exception) => JsonConfig(configItems = List.empty[ConfigItem])
    configData
  }

  private def getAlternate(alternate: String, a: String, arr: Arr) = {
    arr.value.head.obj.get(alternate) match
      case Some(v) => v.str
      case _       => a
  }

  private def loadProperties(file: String): LinkedHashMap[String, Value] = {
    val data                                  = loadData(file)
    val json                                  = ujson.read(data.getOrElse(""))
    val jsonMap: LinkedHashMap[String, Value] = json("properties").obj
    jsonMap
  }

  private def getPropertyType(propertyValue: ujson.Obj): String = {
    propertyValue.obj.get("type") match {
      case Some(ujson.Str("array")) =>
        val itemsType: Option[String] = getItemsType(propertyValue)
        s"array${itemsType.map("_" + _).getOrElse("")}"
      case Some(ujson.Str(singleType)) => singleType
      case Some(ujson.Arr(types)) if types.nonEmpty =>
        val filteredTypes = types.filterNot(_.str == "null")
        val itemsType     = getItemsType(propertyValue)
        s"${filteredTypes.headOption.map(_.str).getOrElse("")}${itemsType.map("_" + _).getOrElse("")}"
      case _ => "unknown"
    }
  }

  private def getItemsType(propertyValue: Obj) = {
    val itemsType = propertyValue.obj.get("items").collect { case obj: Obj =>
      getPropertyType(obj)
    }
    itemsType
  }

  private def convertValueFunction(propertyType: String): String => Any = {
    propertyType match {
      case "integer"      => (str: String) => Try(str.toInt).getOrElse(str)
      case "array_string" => (str: String) => if (str.isEmpty) "" else str.split(ARRAY_SPLIT_CHAR)
      case "array_integer" =>
        (str: String) => if (str.isEmpty) "" else str.split(ARRAY_SPLIT_CHAR).map(s => Try(s.toInt).getOrElse(s))
      case "boolean" =>
        (str: String) =>
          str.toUpperCase match {
            case "YES" => true
            case "NO"  => false
            case _     => str
          }
      case _ => (str: String) => str
    }
  }
