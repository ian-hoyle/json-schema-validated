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

  type DataValidationResult[A] = ValidatedNel[ValidationErrors, A]

  import scala.jdk.CollectionConverters.*

  def validateRequiredSchema(schemaFile: Option[String],propertyToAlt:String=>String)(data: List[RowData]): DataValidationResult[List[RowData]] = {
    schemaValidated(schemaFile, false,propertyToAlt)(data)
  }

  def schemaValidated(schemaFile: Option[String], all: Boolean = true,propertyToAlt:String=>String=(x:String)=> x)(data: List[RowData]): DataValidationResult[List[RowData]] = {
    val jsonSchema = getJsonSchema(schemaFile.get)
    val messagesProvider:String => String = x => "message" // TODO get Messages probably by convention properties file same as schema file name but with .properties ext
    val processData = if (!all)
      List(data.head)
    else
      data

    val errors: Seq[(Option[String], Set[ValidationMessage], Map[String, Any])] = processData.map(x => (x.assetId, jsonSchema.validate(x.json.get, InputFormat.JSON).asScala.toSet, x.data))
    val conErr: Seq[(Option[String], Set[JsonSchemaValidationError])] = errors.map(x => (x._1, convertValidationMessageToError(x._2,messagesProvider, x._3,propertyToAlt)))
    val filtered: Seq[(Option[String], Set[JsonSchemaValidationError])] = conErr.filter(x => x._2.nonEmpty).toList
    if (filtered.isEmpty)
      data.valid
    else
      val validationErrorsList: Seq[ValidationErrors] = filtered.map(x => ValidationErrors(x._1.getOrElse("b"), x._2))
      NonEmptyList.fromList[ValidationErrors](validationErrorsList.toSet.toList).get.invalid
  }

  // needs fixing up
  private def convertValidationMessageToError(schemaValidationMessages: Set[ValidationMessage], messageProvider:String=>String, originalData:Map[String,Any],propertyToAlt:String=>String): Set[JsonSchemaValidationError] = {
    for {
      message <- schemaValidationMessages
      vE = {
        val propertyName = Option(message.getProperty).getOrElse(message.getInstanceLocation.getName(0))
        val originalProperty = propertyToAlt(propertyName)
        val originalValue = originalData.getOrElse(originalProperty, "")
        JsonSchemaValidationError("jsonValidationErrorReason", originalProperty, message.getMessageKey, messageProvider(s"$propertyName:${message.getMessageKey}") , originalValue.toString)
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
