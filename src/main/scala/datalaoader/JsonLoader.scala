package datalaoader

import cats.*
import cats.syntax.all.catsSyntaxValidatedId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import validation.{DataValidationResult, RowData}

import scala.util.{Try, Using}

object JsonLoader:

  def loadJsonListData(
      csvFile: String,
      idColumn: Option[String]
  ): DataValidationResult[List[RowData]] = {
    val loaded = loadJson(csvFile, idColumn)
    loaded.valid
  }

  private def loadJson(csvFile: String, idColumn: Option[String]): List[RowData] = {
    val data: Try[List[RowData]] = Using {
      LoaderUtils.getSourceFromPath(csvFile)
    } { source =>
      val jsonString = source.getLines().mkString
      val mapper     = new ObjectMapper()
      mapper.registerModule(DefaultScalaModule)
      val rows: List[Map[String, String]] =
        mapper.readValue(jsonString, classOf[List[Map[String, String]]])
      rows.map(convertToRowData(idColumn))
    }
    data.getOrElse(List.empty[RowData])
  }

  private def convertToRowData(idColumn: Option[String])(data: Map[String, String]): RowData = {
    val assetId = getAssetId(idColumn, data)
    RowData(None, assetId, data, None)
  }

  private def getAssetId(idKey: Option[String], data: Map[String, String]): Option[String] = {
    for {
      id    <- idKey
      value <- data.get(id)
    } yield value
  }
