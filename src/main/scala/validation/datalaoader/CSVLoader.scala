package validation.datalaoader

import cats.*
import cats.effect.IO
import cats.syntax.all.catsSyntaxValidatedId
import com.github.tototoshi.csv.CSVReader
import validation.{RowData, ValidatorConfiguration}
import validation.jsonschema.ValidatedSchema
import validation.jsonschema.ValidatedSchema.CSVValidationResult

import java.net.URI
import scala.io.Source


object CSVLoader:

  def loadCSVData(csvFile:String,idColumn:Option[String]): CSVValidationResult[List[RowData]] = {
    val loaded = loadCSV(csvFile,idColumn)
    loaded.valid
  }

  private def loadCSV(csvFile:String,idColumn:Option[String]): List[RowData] = {
    val source = if (csvFile.startsWith("http"))
      Source.fromURL(URI.create(csvFile).toASCIIString)
    else
      Source.fromResource(csvFile)

    val cSVReader: CSVReader = CSVReader.open(source)
    cSVReader.allWithHeaders().map(convertToRowData(idColumn))
      .zipWithIndex
      .map((data, index) => data.copy(row_number = Some(index + 1)))
  }

  private def convertToRowData(idColumn:Option[String])(data: Map[String, String]): RowData = {
    val assetId = getAssetId(idColumn, data)
    RowData(None, assetId, data, None)
  }

  private def getAssetId(idKey: Option[String], data: Map[String, String]): Option[String] = {
    for {
      id <- idKey
      value <- data.get(id)
    } yield value
  }




