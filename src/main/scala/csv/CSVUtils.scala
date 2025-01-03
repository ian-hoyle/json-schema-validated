package csv

import cats.*
import cats.implicits.*
import cats.syntax.all.catsSyntaxValidatedId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.tototoshi.csv.CSVReader
import validation.CSVValidatorConfiguration
import validation.jsonschema.ValidatedSchema.CSVValidationResult

case class RowData(row_number: Option[Int], assetId: Option[String], data: Map[String, Any], json: Option[String] = None)

object CSVUtils:

  def loadCSVData(validatorConfiguration: CSVValidatorConfiguration): CSVValidationResult[List[RowData]] = {
    val loaded = loadCSV(validatorConfiguration)
    loaded.valid
  }

  private def loadCSV(validatorConfig: CSVValidatorConfiguration): List[RowData] = {
    //TODO fix csv file location
    val cSVReader: CSVReader = CSVReader.open(s"src/test/resources/${validatorConfig.csvFile}")
    cSVReader.allWithHeaders().map(convertToRowData(validatorConfig))
      .zipWithIndex
      .map((data, index) => data.copy(row_number = Some(index + 1)))
  }

  private def convertToRowData(validatorConfig: CSVValidatorConfiguration)(data: Map[String, String]): RowData = {
    val assetId = getAssetId(validatorConfig.idKey, data)
    val jsonData = convertToJSONString(validatorConfig, data )
    RowData(None, assetId, data,Some(jsonData))
  }


  private def getAssetId(idKey: Option[String], data: Map[String, String]): Option[String] = {
    for {
      id <- idKey
      value <- data.get(id)
    } yield value
  }

  private def convertToJSONString(csvConfig: CSVValidatorConfiguration, data: Map[String, String]): String = {
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



