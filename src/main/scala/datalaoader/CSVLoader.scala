package datalaoader

import cats.*
import cats.effect.IO
import cats.implicits.*
import cats.syntax.all.catsSyntaxValidatedId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.tototoshi.csv.CSVReader
import validation.JsonSchemaValidated.convertToJSONString
import validation.ValidatorConfiguration
import validation.jsonschema.ValidatedSchema
import validation.jsonschema.ValidatedSchema.CSVValidationResult

import java.net.URI
import scala.io.Source

case class RowData(row_number: Option[Int], assetId: Option[String], data: Map[String, Any], json: Option[String] = None)

object CSVUtils:

  private def loadCSVData(validatorConfiguration: ValidatorConfiguration): CSVValidationResult[List[RowData]] = {
    val loaded = loadCSV(validatorConfiguration)
    loaded.valid
  }

  private def loadCSV(validatorConfig: ValidatorConfiguration): List[RowData] = {
    val source = if(validatorConfig.fileToValidate.startsWith("http"))
                   Source.fromURL(URI.create(validatorConfig.fileToValidate).toASCIIString)
    else
      Source.fromResource(validatorConfig.fileToValidate)

    val cSVReader: CSVReader = CSVReader.open(source)
    cSVReader.allWithHeaders().map(convertToRowData(validatorConfig))
      .zipWithIndex
      .map((data, index) => data.copy(row_number = Some(index + 1)))
  }

  private def convertToRowData(validatorConfig: ValidatorConfiguration)(data: Map[String, String]): RowData = {
    val assetId = getAssetId(validatorConfig.idKey, data)
    val jsonData = convertToJSONString(data,validatorConfig.altToProperty,validatorConfig.valueMapper )
    RowData(None, assetId, data,Some(jsonData))
  }


  private def getAssetId(idKey: Option[String], data: Map[String, String]): Option[String] = {
    for {
      id <- idKey
      value <- data.get(id)
    } yield value
  }

  def csvFileValidations(csvConfiguration: ValidatorConfiguration): IO[CSVValidationResult[List[RowData]]] = {
    IO({
      // UTF 8 check to be added first
      loadCSVData(csvConfiguration)
        .andThen(ValidatedSchema.requiredSchemaValidated(csvConfiguration.requiredSchema))
    })
  }




