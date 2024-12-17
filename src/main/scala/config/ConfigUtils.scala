package config

import ujson.Value
import upickle.core.LinkedHashMap

import scala.collection.mutable
import scala.util.Try

object ConfigUtils:

  def getConversionFunction(propertyName: String): String => Any = CSVConfig.config.valueMap.getOrElse(propertyName, (x: String) => x)
  
  
  
  def createValueConversionMap(json:Value):Map[String,String => Any]= {
    val jsonMap: LinkedHashMap[String, Value] =  json("properties").obj
    val d: mutable.Map[String, String => Any] = jsonMap.map { case (k, v) => k -> convertValueFunction(getPropertyType(v.obj))}
    d.toMap
  }

  def createAlternateMaps(json:Value): Map[String, Map[String, String]] = {
    val jsonMap: LinkedHashMap[String, Value] =  json("properties").obj
    val empty =  Map.empty[String,Map[String,String]]
    jsonMap.foldLeft(empty)((b,c)=>b)
  }
  
  private def getPropertyType(propertyValue: ujson.Obj): String = {
    propertyValue.obj.get("type") match {
      case Some(ujson.Str(singleType)) => singleType
      case Some(ujson.Arr(types)) if types.nonEmpty => types.head.str
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
            case "YES" => true
            case "NO" => false
            case _ => str
          }
      case _ => (str: String) => str
    }
  }


