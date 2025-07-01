package datalaoader

import cats.*
import cats.syntax.all.catsSyntaxValidatedId
import com.github.tototoshi.csv.CSVReader
import validation.{DataValidation, RowData}

import scala.util.{Try, Using}

object CSVLoader:

  def loadCSVData(
      csvFile: String,
      idColumn: Option[String]
  ): DataValidation = {
    val loaded = loadCSV(csvFile, idColumn)
    loaded.valid
  }

  def loadCSV(csvFile: String, idColumn: Option[String]): List[RowData] = {
    val data: Try[List[RowData]] = Using { LoaderUtils.getSourceFromPath(csvFile) } { source =>
      val cSVReader: CSVReader = CSVReader.open(source)
      cSVReader
        .allWithHeaders()
        .map(convertToRowData(idColumn))
        .zipWithIndex
        .map((data, index) => data.copy(row_number = Some(index + 1)))
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
