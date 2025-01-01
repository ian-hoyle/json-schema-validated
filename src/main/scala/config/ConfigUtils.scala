package config

import ujson.Value
import upickle.core.LinkedHashMap

import java.net.URI
import scala.io.Source
import scala.util.{Try, Using}

object ConfigUtils:

  def loadProperties(file: String): LinkedHashMap[String, Value] = {
    val data = {
      if (file.startsWith("http"))
        Using(Source.fromURL(URI.create(file).toASCIIString))(_.mkString)
      else
        Using(Source.fromResource(file))(_.mkString)
    }
    val json = ujson.read(data.getOrElse(""))
    val jsonMap: LinkedHashMap[String, Value] = json("properties").obj
    jsonMap
  }

  def getPropertyType(propertyValue: ujson.Obj): String = {
    propertyValue.obj.get("type") match {
      case Some(ujson.Str(singleType)) => singleType
      case Some(ujson.Arr(types)) if types.nonEmpty => types.head.str
      case _ => "unknown"
    }
  }

  def convertValueFunction(propertyType: String): String => Any = {
    propertyType match {
      case "integer" => (str: String) => Try(str.toInt).getOrElse(str)
      case "array" => (str: String) => if (str.isEmpty) "" else str.split("\\|")
      case "boolean" =>
        (str: String) =>
          str.toUpperCase match {
            case "YES" => true
            case "NO" => false
            case _ => str
          }
      case _ => (str: String) => str
    }
  }


