package validation.config

import ConfigUtils.{convertValueFunction, getPropertyType, loadProperties}
import cats.data.Reader
import cats.effect.IO
import ujson.{Arr, Value}
import upickle.core.LinkedHashMap
import validation.{Parameters, ValidatorConfiguration}

import scala.collection.mutable

object ValidationConfig:

  def alternateKeyToPropertyMapper(parameters: Parameters): String => String =
    val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.csConfig)
    if (parameters.alternates.isEmpty)
      (x: String) => x
    else
      val r: Map[String, String] = jsonMap.toMap.map({
        case (a, c) => (c.obj.get("alternateKeys") match {
          case Some(arr: Arr) => getAlternate(parameters, a, arr)
          case _ => a
        }, a)
      })
      (x: String) => r.getOrElse(x, x)

  def propertyToAlternateKeyMapper(parameters: Parameters): String => String =
    if (parameters.alternates.isEmpty)
      (x: String) => x
    else
      val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.csConfig)
      val r: Map[String, String] = jsonMap.toMap.map({
        case (a, c) => (a, c.obj.get("alternateKeys") match {
          case Some(arr: ujson.Arr) => getAlternate(parameters, a, arr)
          case _ => a
        })
      })
      (x: String) => r.getOrElse(x, x)


  def stringValueMapper(parameters: Parameters)(property: String): String => Any = {
    val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.csConfig)
    val propertyToValueConversionMap: mutable.Map[String, String => Any] = jsonMap.map { case (k, v) => k -> convertValueFunction(getPropertyType(v.obj)) }
    propertyToValueConversionMap.getOrElse(property, (x:Any) => x)
  }

  private def getAlternate(parameters: Parameters, a: String, arr: Arr) = {
    arr.value.head.obj.get(parameters.alternates.get) match
      case Some(v) => v.str
      case _ => a
  }

  def prepareValidationConfiguration(parameters: Parameters): IO[ValidatorConfiguration] = {
    IO({
      val csvConfigurationReader = for {
        altHeaderToPropertyMapper <- Reader(ValidationConfig.alternateKeyToPropertyMapper)
        propertyToAltHeaderMapper <- Reader(ValidationConfig.propertyToAlternateKeyMapper)
        valueMapper <- Reader(ValidationConfig.stringValueMapper)
      } yield ValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper,
        valueMapper, parameters.fileToValidate, parameters.idKey,
        parameters.requiredSchema, parameters.schema)
      csvConfigurationReader.run(parameters)
    }
    ) //TODO handle error with raiseError that contains ValidationResult
  }









