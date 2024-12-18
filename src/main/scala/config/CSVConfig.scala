package config

import cats.data.Reader
import config.ConfigUtils.createValueConversionMap
import ujson.Value

import scala.io.Source

object CSVConfig:
  
  case class Config(valueMap: Map[String, String => Any])
  lazy val config : Config = {
    val jsonConfigFileName = "DaBase.json"
    val configData = loadResourceFile(jsonConfigFileName)
   
    val dbInfoReader: Reader[Value, Config] = for {
      funcMap <- Reader(createValueConversionMap)
    } yield Config(funcMap)

    dbInfoReader.run(ujson.read(configData))
  }
  
 
  
  def loadResourceFile(fileName: String): String = {
    val resourceStream = getClass.getResourceAsStream(s"/$fileName")
    require(resourceStream != null, s"Resource file '$fileName' not found.")
    val source = Source.fromInputStream(resourceStream)
    try source.mkString
    finally source.close()
  }







