package config

import config.ConfigUtils.{convertValueFunction, getPropertyType, loadProperties}
import ujson.{Arr, Value}
import upickle.core.LinkedHashMap
import validation.Parameters

import scala.collection.mutable

object CSVParserConfig:

  def alternateKeyToPropertyMapper(parameters: validation.Parameters): String => String =
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

  def propertyToAlternateKeyMapper(parameters: validation.Parameters): String => String =
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


  def csvStringToValueMapper(parameters: Parameters)(property: String): String => Any = {
    val jsonMap: LinkedHashMap[String, Value] = loadProperties(parameters.csConfig)
    val d: mutable.Map[String, String => Any] = jsonMap.map { case (k, v) => k -> convertValueFunction(getPropertyType(v.obj)) }
    d.getOrElse(property, (x: String) => x)
  }

  private def getAlternate(parameters: Parameters, a: String, arr: Arr) = {
    arr.value.head.obj.get(parameters.alternates.get) match
      case Some(v) => v.str
      case _ => a
  }










