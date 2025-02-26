package validation.datalaoader

import cats.*
import cats.syntax.all.catsSyntaxValidatedId
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.github.tototoshi.csv.CSVReader
import validation.RowData
import validation.jsonschema.ValidatedSchema.DataValidationResult

import java.net.URI
import scala.io.Source


object CSVLoader:

  def loadCSVData(csvFile: String, idColumn: Option[String]): DataValidationResult[List[RowData]] = {
    val loaded = loadCSV(csvFile, idColumn)
    loaded.valid
  }

  private def loadCSV(csvFile: String, idColumn: Option[String]): List[RowData] = {
    val source = if (csvFile.startsWith("http"))
      Source.fromURL(URI.create(csvFile).toASCIIString)
    else if (csvFile.startsWith("s3://"))
      Source.fromInputStream(getS3ObjectInputStream(csvFile))
    else
      Source.fromResource(csvFile)

    val cSVReader: CSVReader = CSVReader.open(source)
    cSVReader.allWithHeaders().map(convertToRowData(idColumn))
      .zipWithIndex
      .map((data, index) => data.copy(row_number = Some(index + 1)))
  }


  private def getS3ObjectInputStream(s3Uri: String): S3ObjectInputStream = {
    val s3Client: AmazonS3 = AmazonS3ClientBuilder.defaultClient()
    val uri = new URI(s3Uri)
    val bucketName = uri.getHost
    val key = uri.getPath.substring(1) // Remove leading slash
    val s3Object = s3Client.getObject(bucketName, key)
    s3Object.getObjectContent
  }

  private def convertToRowData(idColumn: Option[String])(data: Map[String, String]): RowData = {
    val assetId = getAssetId(idColumn, data)
    RowData(None, assetId, data, None)
  }

  private def getAssetId(idKey: Option[String], data: Map[String, String]): Option[String] = {
    for {
      id <- idKey
      value <- data.get(id)
    } yield value
  }




