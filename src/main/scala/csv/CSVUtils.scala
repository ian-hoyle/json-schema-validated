package csv

import cats.*
import cats.implicits.*
import cats.syntax.all.catsSyntaxValidatedId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.tototoshi.csv.CSVReader
import validation.CSVConfiguration
import validation.jsonschema.ValidatedSchema.CSVValidationResult

case class RowData(row_number: Option[Int], assetId: Option[String], data: Map[String, Any], json: Option[String] = None)

object CSVUtils:

  def loadCSVData(csVConfiguration: CSVConfiguration): CSVValidationResult[List[RowData]] = {
    val loaded = loadCSV(csVConfiguration)
    loaded.valid
  }

  private def loadCSV(csvConfig: CSVConfiguration): List[RowData] = {
    val cSVReader: CSVReader = CSVReader.open(s"src/test/resources/${csvConfig.parameters.csvFile}")
    cSVReader.allWithHeaders().map(convertToRowData(csvConfig))
      .zipWithIndex
      .map((data, index) => data.copy(row_number = Some(index + 1)))
  }

  private def convertToRowData(csvConfig: CSVConfiguration)(data: Map[String, String]): RowData = {
    val assetId = getAssetId(csvConfig.parameters.idKey, data)
    val jsonData = convertToJSONString(csvConfig, data )
    RowData(None, assetId, data,Some(jsonData))
  }


  private def getAssetId(idKey: Option[String], data: Map[String, String]): Option[String] = {
    for {
      id <- idKey
      value <- data.get(id)
    } yield value
  }

  private def convertToJSONString(csvConfig: CSVConfiguration,data: Map[String, String]): String = {
    val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
    val convertedData = data.map {
      case (header, value) =>
        val property = csvConfig.altToProperty(header)
        (property,
          if (value.isEmpty) null
          else csvConfig.valueMapper(property)(value)
        )
    }
    val generatedJson = mapper.writeValueAsString(convertedData)
    generatedJson
  }



