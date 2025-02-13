package validation.config

import ConfigUtils.{convertValueFunction, getPropertyType, loadProperties}
import cats.data.Reader
import cats.effect.IO
import ujson.{Arr, Value}
import upickle.core.LinkedHashMap
import validation.{ConfigParameters, Parameters, ValidatorConfiguration}

import scala.collection.mutable

object ValidationConfig:

  def alternateKeyToPropertyMapper(parameters: ConfigParameters): String => String =
    val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.csConfig)
    if (parameters.alternates.isEmpty)
      (x: String) => x
    else
      val r: Map[String, String] = jsonMap.toMap.map({
        case (a, c) => (c.obj.get("alternateKeys") match {
          case Some(arr: Arr) => getAlternate(parameters.alternates.get, a, arr)
          case _ => a
        }, a)
      })
      (x: String) => r.getOrElse(x, x)

  def propertyToInAlternateKeyMapper(parameters: ConfigParameters): String => String =
    if (parameters.alternates.isEmpty)
      (x: String) => x
    else
      val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.csConfig)
      val r: Map[String, String] = jsonMap.toMap.map({
        case (a, c) => (a, c.obj.get("alternateKeys") match {
          case Some(arr: ujson.Arr) => getAlternate(parameters.alternates.get, a, arr)
          case _ => a
        })
      })
      (x: String) => r.getOrElse(x, x)


  def stringValueMapper(parameters: ConfigParameters):(property: String,value: String) => Any = {
    val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.csConfig)
    val propertyToValueConversionMap: mutable.Map[String, String => Any] = jsonMap.map { case (k, v) => k -> convertValueFunction(getPropertyType(v.obj)) }
    (property: String, value: String) => propertyToValueConversionMap.getOrElse(property, (x:Any) => x)(value)
  }

  private def getAlternate(alternate: String, a: String, arr: Arr) = {
    arr.value.head.obj.get(alternate) match
      case Some(v) => v.str
      case _ => a
  }

  def prepareValidationConfiguration(configFile: String, alternateKey: Option[String]): IO[ValidatorConfiguration] = {
    IO({
      val csvConfigurationReader = for {
        altHeaderToPropertyMapper <- Reader(ValidationConfig.alternateKeyToPropertyMapper)
        propertyToAltHeaderMapper <- Reader(ValidationConfig.propertyToInAlternateKeyMapper)
        valueMapper <- Reader(ValidationConfig.stringValueMapper)
      } yield ValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper,
        valueMapper)
      csvConfigurationReader.run(ConfigParameters(configFile, alternateKey))
    }
    ) //TODO handle error with raiseError that contains ValidationResult
  }









