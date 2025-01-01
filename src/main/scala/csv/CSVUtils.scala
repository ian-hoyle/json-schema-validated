package csv

import cats.*
import cats.implicits.*
import cats.syntax.all.catsSyntaxValidatedId
import com.github.tototoshi.csv.CSVReader
import validation.CSVConfiguration
import validation.jsonschema.ValidatedSchema.CSVValidationResult

case class RowData(row_number: Option[Int], assetId: Option[String], data: Map[String, Any])

object CSVUtils:

  def loadCSVData(csVConfiguration: CSVConfiguration): CSVValidationResult[List[RowData]] = {
    val loaded = loadCSV(csVConfiguration)
    loaded.valid
  }

  private def loadCSV(csvConfig: CSVConfiguration): List[RowData] = {
    val cSVReader: CSVReader = CSVReader.open(s"src/test/resources/${csvConfig.csv}")
    cSVReader.allWithHeaders().map(convertToRowData(csvConfig))
      .zipWithIndex
      .map((data, index) => data.copy(row_number = Some(index + 1)))
  }

  private def convertToRowData(csvConfig: CSVConfiguration)(data: Map[String, String]): RowData = {
    val assetId = getAssetId(csvConfig.idKey, data)
    val convertedData = data.map {
      case (header, value) =>

        val property = csvConfig.altToProperty(header)
        (property,
          if (value.isEmpty) null
          else csvConfig.valueMapper(property)(value)
        )
    }
    RowData(None, assetId, convertedData)
  }

  private def getAssetId(idKey: Option[String], data: Map[String, String]): Option[String] = {
    for {
      id <- idKey
      value <- data.get(id)
    } yield value
  }



