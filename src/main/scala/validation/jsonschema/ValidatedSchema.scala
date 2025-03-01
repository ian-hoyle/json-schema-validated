package validation.jsonschema

import cats.*
import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.all.catsSyntaxValidatedId
import com.networknt.schema.*
import validation.RowData
import validation.error.{JsonSchemaValidationError, ValidationErrors}

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import java.util.Properties
import scala.io.Source
import scala.util.{Try, Using}


object ValidatedSchema:

  type DataValidationResult[A] = ValidatedNel[ValidationErrors, A]

  import scala.jdk.CollectionConverters.*

  def validateRequiredSchema(schemaFile: Option[String], propertyToAlt: String => String)(data: List[RowData]): DataValidationResult[List[RowData]] =
    schemaFile match {
      case Some(schema) => schemaValidated(schema, false, propertyToAlt)(data)
      case None => data.valid
    }


  def schemaValidated(schemaFile: String, all: Boolean = true, propertyToAlt: String => String = (x: String) => x)(data: List[RowData]): DataValidationResult[List[RowData]] = {
    val jsonSchema = getJsonSchema(schemaFile)
    val messagesProvider: String => String = loadProperties(schemaFile)
    val processData = if (!all)
      List(data.head)
    else
      data

    case class SchemaValidationErrors(assetId: Option[String], errors: Set[ValidationMessage], data: Map[String, Any])
    val errors: List[SchemaValidationErrors] = processData.map(data =>
      SchemaValidationErrors(data.assetId,
        jsonSchema.validate(data.json.get, InputFormat.JSON).asScala.toSet,
        data.data))

    case class ConvertedErrors(assetId: Option[String], errors: Set[JsonSchemaValidationError])
    val convertedErrors: List[ConvertedErrors] = errors.map(validationError =>
      ConvertedErrors(validationError.assetId,
        convertSchemaValidationErrorToJSValidationError(validationError.errors,
          schemaFile,
          messagesProvider,
          validationError.data,
          propertyToAlt)))

    val filtered: List[ConvertedErrors] = convertedErrors.filter(x => x.errors.nonEmpty)

    if (filtered.isEmpty)
      data.valid
    else
      val validationErrorsList: Seq[ValidationErrors] = filtered.map(x => ValidationErrors(x.assetId.getOrElse(""), x.errors))
      NonEmptyList.fromList[ValidationErrors](validationErrorsList.toSet.toList).get.invalid
  }

  // needs fixing up
  private def convertSchemaValidationErrorToJSValidationError(schemaValidationMessages: Set[ValidationMessage],
                                                              schemaFile: String,
                                                              messageProvider: String => String,
                                                              originalData: Map[String, Any],
                                                              propertyToAlt: String => String): Set[JsonSchemaValidationError] = {
    for {
      message <- schemaValidationMessages
      vE = {
        val propertyName = Option(message.getProperty).getOrElse(message.getInstanceLocation.getName(0))
        val originalProperty = propertyToAlt(propertyName)
        val originalValue = originalData.getOrElse(propertyName, "")
        JsonSchemaValidationError(schemaFile, originalProperty, message.getMessageKey, messageProvider(s"$propertyName.${message.getMessageKey}"), originalValue.toString)
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

  def loadProperties(jsonFileName: String)(key: String): String = {
    val propertiesFileName = jsonFileName.replace(".json", ".properties")
    val properties = new Properties()

    Using(Source.fromResource(propertiesFileName)) { source =>
      properties.load(source.bufferedReader())
    }
    properties.getProperty(key, key)
  }

  private case class ValidationError(reason: String, propertyName: String, key: String)

def loadData(mySchema: String): Try[String] = {
  val data: Try[String] = {
    if (mySchema.startsWith("http"))
      Using(Source.fromURL(URI.create(mySchema).toASCIIString))(_.mkString)
    else
      Using(Source.fromResource(mySchema))(_.mkString)
  }
  data
}
