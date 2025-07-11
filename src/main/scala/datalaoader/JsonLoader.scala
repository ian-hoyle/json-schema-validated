package datalaoader

import cats.*
import cats.syntax.all.catsSyntaxValidatedId
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import validation.{DataValidation, Data}

import scala.util.{Try, Using}

object JsonLoader:

  def loadJsonListData(
      jsonListFile: String,
      idColumn: Option[String]
  ): DataValidation = {
    val loaded = loadJson(jsonListFile, idColumn)
    loaded.valid
  }

  private def loadJson(jsonListFile: String, idColumn: Option[String]): List[Data] = {
    val data: Try[List[Data]] = Using {
      LoaderUtils.getSourceFromPath(jsonListFile)
    } { source =>
      val jsonString = source.getLines().mkString
      val mapper     = new ObjectMapper()
      mapper.registerModule(DefaultScalaModule)

      // Create type reference for List[Map[String, Any]]
      val typeRef                      = new TypeReference[List[Map[String, Any]]]() {}
      val rows: List[Map[String, Any]] = mapper.readValue(jsonString, typeRef)

      rows.map(convertToData(idColumn))
    }
    data.getOrElse(List.empty[Data])
  }

  private def convertToData(idColumn: Option[String])(data: Map[String, Any]): Data = {
    val assetId = getAssetId(idColumn, data)
    Data(None, assetId, data, None)
  }

  private def getAssetId(idKey: Option[String], data: Map[String, Any]): Option[String] = {
    for {
      id    <- idKey
      value <- data.get(id)
    } yield value.toString
  }
