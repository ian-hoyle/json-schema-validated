package validation.jsonschema

import cats.*
import cats.data.NonEmptyList
import cats.syntax.all.catsSyntaxValidatedId
import com.networknt.schema.*
import validation.error.{JsonSchemaValidationError, ValidationErrors}
import validation.{ConvertedErrors, DataValidationResult, RowData, SchemaValidationErrors}

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import java.util.Properties
import scala.collection.mutable
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}


object ValidatedSchema:

  private lazy val loadedSchema = mutable.Map.empty[String, JsonSchema]


  def validateSchemaSingleRow(schemaFile: Option[String], propertyToAlt: String => String)(data: List[RowData]): DataValidationResult[List[RowData]] =
    schemaFile match {
      case Some(schema) => schemaValidated(schema, propertyToAlt)(List(data.head)).map(_ => data)
      case None => data.valid
    }

  // ToDo: Should return Invalid if no JSON in RowData
  def schemaValidated(schemaFile: String, propertyToAlt: String => String = (x: String) => x)(data: List[RowData]): DataValidationResult[List[RowData]] = {

    val jsonSchema = getLoadedSchema(schemaFile)
    val messagesProvider: MessageOption => String = loadMessages(schemaFile.replace(".json", ".properties"))

    val errors: List[SchemaValidationErrors] = data.map(data =>
      SchemaValidationErrors(data.assetId,
        jsonSchema.validate(data.json.get, InputFormat.JSON).asScala.toSet,
        data.data))

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

  private def convertSchemaValidationErrorToJSValidationError(schemaValidationMessages: Set[ValidationMessage],
                                                              schemaFile: String,
                                                              messageProvider: MessageOption => String,
                                                              originalData: Map[String, Any],
                                                              propertyToAlt: String => String): Set[JsonSchemaValidationError] = {
    for {
      message <- schemaValidationMessages
      validationError = {
        val propertyName = Option(message.getProperty).getOrElse(message.getInstanceLocation.getName(0))
        val originalProperty = propertyToAlt(propertyName)
        val originalValue = originalData.getOrElse(propertyName, message.getInstanceNode.asText)
        JsonSchemaValidationError(schemaFile, originalProperty, message.getMessageKey, messageProvider(MessageOption(s"$propertyName.${message.getMessageKey}",Some(message.getMessage))), originalValue.toString)
      }
    } yield validationError
  }

  private case class MessageOption(key:String, alternateMessage:Option[String])
  private def loadMessages(propertiesFileName: String)(messageOption: MessageOption): String = {
    val properties = new Properties()

    val alternative = messageOption.alternateMessage match {
      case Some(message) => message
      case None => messageOption.key
    }
    Using(Source.fromResource(propertiesFileName)) { source =>
      properties.load(source.bufferedReader())
    }
    properties.getProperty(messageOption.key, alternative)
  }

  private def getLoadedSchema(schemaFile: String): JsonSchema = {
    loadedSchema.get(schemaFile) match {
      case Some(schema) => schema
      case None =>
        val jsonSchema = getJsonSchema(schemaFile)
        loadedSchema.put(schemaFile, jsonSchema)
        jsonSchema
    }
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

  def loadData(mySchema: String): Try[String] = {
    val data: Try[String] = {
      if (mySchema.startsWith("http"))
        Using(Source.fromURL(URI.create(mySchema).toASCIIString))(_.mkString)
      else
        Using(Source.fromResource(mySchema))(_.mkString)
    }
    data
  }

  private case class ValidationError(reason: String, propertyName: String, key: String)
