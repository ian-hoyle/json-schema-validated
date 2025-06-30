package datalaoader

import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

import java.net.URI
import scala.io.Source

object LoaderUtils {

  def getSourceFromPath(csvFile: String): Source = {
    csvFile match {
      case _ if csvFile.startsWith("http") || csvFile.startsWith("file:") =>
        Source.fromURL(URI.create(csvFile).toASCIIString)
      case _ if csvFile.startsWith("s3://") =>
        Source.fromInputStream(getS3ObjectInputStream(csvFile))
      case _ =>
        Source.fromResource(csvFile)
    }
  }

  private def getS3ObjectInputStream(s3Uri: String): S3ObjectInputStream = {
    val s3Client: AmazonS3 = AmazonS3ClientBuilder.defaultClient()
    val uri                = new URI(s3Uri)
    val bucketName         = uri.getHost
    val key                = uri.getPath.substring(1) // Remove leading slash
    val s3Object           = s3Client.getObject(bucketName, key)
    s3Object.getObjectContent
  }
}
