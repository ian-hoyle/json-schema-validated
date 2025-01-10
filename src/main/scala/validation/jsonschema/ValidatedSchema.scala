package validation.jsonschema

import cats.*
import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.all.catsSyntaxValidatedId
import com.networknt.schema.*
import validation.RowData
import validation.error.{JsonSchemaValidationError, ValidationErrors}

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import scala.io.Source
import scala.util.{Try, Using}


object ValidatedSchema:

  type CSVValidationResult[A] = ValidatedNel[ValidationErrors, A]

  import scala.jdk.CollectionConverters.*

  def requiredSchemaValidated(schemaFile: Option[String])(data: List[RowData]): CSVValidationResult[List[RowData]] = {
    schemaValidated(schemaFile, false)(data)
  }

  def schemaValidated(schemaFile: Option[String], all: Boolean = true)(data: List[RowData]): CSVValidationResult[List[RowData]] = {
    val jsonSchema = getJsonSchema(schemaFile.get)
    val messages = "a" -> "b" // TODO get Messages probably by convention properties file same as schema file name but with .properties ext 
    val processData = if (!all)
      List(data.head)
    else
      data

    val errors: Seq[(Option[String], Set[ValidationMessage], Map[String, Any])] = processData.map(x => (x.assetId, jsonSchema.validate(x.json.get, InputFormat.JSON).asScala.toSet, x.data))
    val conErr: Seq[(Option[String], Set[JsonSchemaValidationError])] = errors.map(x => (x._1, convertValidationMessageToError(x._2)))
    val filtered: Seq[(Option[String], Set[JsonSchemaValidationError])] = conErr.filter(x => x._2.nonEmpty).toList
    if (filtered.isEmpty)
      data.valid
    else
      val r: Seq[ValidationErrors] = filtered.map(x => ValidationErrors(x._1.getOrElse("b"), x._2))
      NonEmptyList.fromList[ValidationErrors](r.toSet.toList).get.invalid
  }

  // needs fixing up
  private def convertValidationMessageToError(messages: Set[ValidationMessage]): Set[JsonSchemaValidationError] = {
    for {
      message <- messages
      vE = {
        val propertyName = Option(message.getProperty).getOrElse(message.getInstanceLocation.getName(0))
        JsonSchemaValidationError("jsonValidationErrorReason", propertyName, message.getMessageKey, "no message")
      }
    } yield vE
  }

  private def getJsonSchema(mySchema: String): JsonSchema = {
    val data: Try[String] = loadData(mySchema)

    val inputStream: InputStream = new ByteArrayInputStream(data.get.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    val schema = JsonMetaSchema.getV202012

    val jsonSchemaFactory = new JsonSchemaFactory.Builder()
      .defaultMetaSchemaIri(SchemaId.V202012)
      .metaSchema(JsonMetaSchema.getV202012)
      .build()

    val schemaValidatorsConfig = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()
    jsonSchemaFactory.getSchema(inputStream, schemaValidatorsConfig)
  }

  private case class ValidationError(reason: String, propertyName: String, key: String)

def loadData(mySchema: String) = {
  val data: Try[String] = {
    if (mySchema.startsWith("http"))
      Using(Source.fromURL(URI.create(mySchema).toASCIIString))(_.mkString)
    else
      Using(Source.fromResource(mySchema))(_.mkString)
  }
  data
}
