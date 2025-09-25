package validation.jsonschema

import cats.*
import cats.data.NonEmptyList
import cats.syntax.all.catsSyntaxValidatedId
import com.networknt.schema.*
import validation.error.{JsonSchemaValidationError, ValidationErrors}
import validation.{ConvertedErrors, DataValidation, Data, SchemaValidationErrors}

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import java.util.Properties
import scala.collection.mutable
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

object ValidatedSchema:

  private lazy val loadedSchema = mutable.Map.empty[String, JsonSchema]
  private val propertiesMap     = mutable.Map.empty[String, Properties]

  def validateJson(schema: String, json: String): DataValidation = {
    val data = Data(None, None, Map.empty[String, Any], Some(json))
    schemaValidated(schema)(List(data))
  }

  /** Validates a list of `Data` against a JSON schema file.
    *
    * @param schemaFile
    *   The path to the JSON schema file to validate against.
    * @param propertyToAlt
    *   A function to map property names to alternate names for error reporting. Defaults to an identity function.
    * @param data
    *   The list of `Data` to validate.
    * @return
    *   A `DataValidationResult` containing either the validated data or validation errors.
    */
  def schemaValidated(schemaFile: String, propertyToAlt: String => String = (x: String) => x)(
      data: List[Data]
  ): DataValidation = {

    val jsonSchema = getLoadedSchema(schemaFile)
    val messagesProvider: MessageOption => String = loadMessages(
      schemaFile.replace(".json", ".properties")
    )

    val errors: List[SchemaValidationErrors] = data.map(data =>
      SchemaValidationErrors(
        data.assetId,
        jsonSchema.validate(data.json.get, InputFormat.JSON).asScala.toSet,
        data.data
      )
    )

    val convertedErrors: List[ConvertedErrors] = errors.map(validationError =>
      ConvertedErrors(
        validationError.assetId,
        convertSchemaValidationErrorToJSValidationError(
          validationError.errors,
          schemaFile,
          messagesProvider,
          validationError.data,
          propertyToAlt
        )
      )
    )

    val filtered: List[ConvertedErrors] = convertedErrors.filter(x => x.errors.nonEmpty)
    if (filtered.isEmpty) data.valid
    else
      val validationErrorsList: Seq[ValidationErrors] =
        filtered.map(x => ValidationErrors(x.assetId.getOrElse(""), x.errors))
      NonEmptyList.fromList[ValidationErrors](validationErrorsList.toSet.toList).get.invalid
  }

  def validateSchemaSingleRow(schemaFile: Option[String], propertyToAlt: String => String)(
      data: List[Data]
  ): DataValidation =
    schemaFile match {
      case Some(schema) => schemaValidated(schema, propertyToAlt)(List(data.head)).map(_ => data)
      case None         => data.valid
    }

  private def convertSchemaValidationErrorToJSValidationError(
      schemaValidationMessages: Set[ValidationMessage],
      schemaFile: String,
      messageProvider: MessageOption => String,
      originalData: Map[String, Any],
      propertyToAlt: String => String
  ): Set[JsonSchemaValidationError] = {
    for {
      message <- schemaValidationMessages
      validationError = {
        val propertyName =
          Option(message.getProperty).getOrElse(message.getInstanceLocation.getName(0))
        val originalProperty = propertyToAlt(propertyName)
        val originalValue    = originalData.getOrElse(propertyName, message.getInstanceNode.asText)
        JsonSchemaValidationError(
          schemaFile,
          originalProperty,
          message.getMessageKey,
          messageProvider(
            MessageOption(s"$propertyName.${message.getMessageKey}", Some(message.getMessage))
          ),
          originalValue.toString
        )
      }
    } yield validationError
  }

  private def loadMessages(propertiesFileName: String)(messageOption: MessageOption): String = {

    val alternative = messageOption.alternateMessage match {
      case Some(message) => message
      case None          => messageOption.key
    }
    val properties = propertiesMap.getOrElseUpdate(
      propertiesFileName, {
        val newProperties = new Properties()
        Using(Source.fromResource(propertiesFileName)) { source =>
          newProperties.load(source.bufferedReader())
        }
        newProperties
      }
    )

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

    val inputStream: InputStream = new ByteArrayInputStream(
      data.get.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    )
    val schema = JsonMetaSchema.getV202012

    val jsonSchemaFactory = new JsonSchemaFactory.Builder()
      .defaultMetaSchemaIri(SchemaId.V202012)
      .metaSchema(JsonMetaSchema.getV202012)
      .build()

    val schemaValidatorsConfig =
      SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()
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

  // The propertyToAlt function is used to map the internal property names to the original input for error reporting.
  def generateSchemaValidatedList(
      schemaFiles: List[String],
      propertyToAlt: String => String
  ): List[List[Data] => DataValidation] = {
    schemaFiles.map { schemaFile => data =>
      ValidatedSchema.schemaValidated(schemaFile, propertyToAlt)(data)
    }
  }

  private case class MessageOption(key: String, alternateMessage: Option[String])

  private case class ValidationError(reason: String, propertyName: String, key: String)
