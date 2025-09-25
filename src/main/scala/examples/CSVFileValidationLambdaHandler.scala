package examples

import cats.data.Validated.*
import cats.effect.IO
import cats.effect.unsafe.implicits.*
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSVData
import validation.jsonschema.ValidationDataUtils.*
import validation.jsonschema.ValidatedSchema.{generateSchemaValidatedList, validateSchemaSingleRow}
import validation.{DataValidation, Parameters, Data, Validation, ValidatorConfiguration}

import scala.jdk.CollectionConverters.*

object CSVFileValidationLambdaHandler extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {

  private val headers = Map[String, String](
    "Access-Control-Allow-Origin"  -> "*",
    "Access-Control-Allow-Methods" -> "OPTIONS, POST",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization"
  )

  override def handleRequest(
      input: APIGatewayProxyRequestEvent,
      context: Context
  ): APIGatewayProxyResponseEvent = {

    val requestBody = input.getBody
    decode[Parameters](requestBody) match {
      case Right(params) =>
        csvFileValidation(params).unsafeRunSync() match
          case Valid(data) =>
            val response = new APIGatewayProxyResponseEvent()
            response.setHeaders(headers.asJava)
            response.setStatusCode(200)
            response
          case Invalid(error) =>
            val response = new APIGatewayProxyResponseEvent()
            response.setBody(error.toList.asJson.noSpaces)
            response.setHeaders(headers.asJava)
            response.setStatusCode(400)
            response
      case Left(error) =>
        val response = new APIGatewayProxyResponseEvent()
        response.setBody(s"Invalid input: ${error.getMessage}")
        response.setHeaders(headers.asJava)
        response.setStatusCode(400)
        response
    }
  }

  def csvFileValidation(parameters: Parameters): IO[DataValidation] = {
    for {
      configuration <- IO(
        prepareValidationConfiguration(
          parameters.configFile,
          parameters.baseSchema
        )
      )
      dataLoader              = loadCSVData(parameters.fileToValidate, parameters.idKey)
      failFastValidationList  = failFastValidations(parameters, configuration)
      combiningValidationList = combiningValidations(parameters.schema, configuration)
      validation <- IO {
        Validation.validate(dataLoader, failFastValidationList, combiningValidationList)
      }
    } yield validation
  }

  def failFastValidations(
      parameters: Parameters,
      configuration: ValidatorConfiguration
  ): List[List[Data] => DataValidation] = {
    List(
      mapKeys(configuration.domainKeyToProperty("TDRMetadataUpload")),
      addJsonForValidation(configuration.valueMapper),
      validateSchemaSingleRow(parameters.requiredSchema, configuration.propertyToDomainKey("TDRMetadataUpload"))
    )
  }

  def combiningValidations(
      schemas: List[String],
      configuration: ValidatorConfiguration
  ): List[List[Data] => DataValidation] = {
    generateSchemaValidatedList(schemas, configuration.propertyToDomainKey("TDRMetadataUpload"))
  }
}
