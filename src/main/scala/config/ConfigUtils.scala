package config

import ujson.Value
import upickle.core.LinkedHashMap
import validation.jsonschema.loadData

import scala.util.Try

object ConfigUtils:

  def loadProperties(file: String): LinkedHashMap[String, Value] = {
    val data = loadData(file)
    val json = ujson.read(data.getOrElse(""))
    val jsonMap: LinkedHashMap[String, Value] = json("properties").obj
    jsonMap
  }

  def getPropertyType(propertyValue: ujson.Obj): String = {
    propertyValue.obj.get("type") match {
      case Some(ujson.Str(singleType)) => singleType
      case Some(ujson.Arr(types)) if types.nonEmpty => types.head.str // TODO correctly handle arrays. Assuming string before null etc
      case _ => "unknown"
    }
  }

  def convertValueFunction(propertyType: String): Any => Any = {
    propertyType match {
      case "integer" => (str:Any) => Try(str.toString.toInt).getOrElse(str)
      case "array" => (str: Any) => if (str.toString.isEmpty) "" else str.toString.split("\\|")
      case "boolean" =>
        (str: Any) =>
          str.toString.toUpperCase match {
            case "YES" | "true" => true
            case "NO" | "false" => false
            case _ => str.toString
          }
      case _ => (str:Any) => str
    }
  }


