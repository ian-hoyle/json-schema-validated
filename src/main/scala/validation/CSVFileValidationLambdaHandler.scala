package validation

import cats.data.Validated.*
import cats.effect.IO
import cats.effect.unsafe.implicits.*
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import validation.config.ValidationConfig.prepareValidationConfiguration
import validation.datalaoader.CSVLoader.loadCSVData
import validation.jsonschema.JsonSchemaValidated.*
import validation.jsonschema.ValidatedSchema.{DataValidationResult, validateRequiredSchema}
import scala.jdk.CollectionConverters.*

object CSVFileValidationLambdaHandler extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {


  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {

    val requestBody = input.getBody

    decode[Parameters](requestBody) match {
      case Right(params) =>
        csvFileValidation(params).unsafeRunSync() match
          case Valid(data) =>
            val response = new APIGatewayProxyResponseEvent()
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

  def csvFileValidation(parameters: Parameters): IO[DataValidationResult[List[RowData]]] = {
    for {
      configuration <- prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)
      data <- IO(
        loadCSVData(parameters.fileToValidate, parameters.idKey)
          andThen mapKeys(configuration.altInToKey)
          andThen addJsonForValidation(configuration.valueMapper)
          andThen validateRequiredSchema(parameters.requiredSchema, configuration.keyToAltIn)
      )
      validation <- validateWithMultipleSchema(data, parameters.schema, configuration.keyToAltIn)
    } yield validation
  }

  private val headers = Map[String, String](
    "Access-Control-Allow-Origin" -> "https://ian-hoyle.github.io",
    "Access-Control-Allow-Methods" -> "OPTIONS, POST",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization",
    "Access-Control-Allow-Credentials" -> "true"
  )
}

