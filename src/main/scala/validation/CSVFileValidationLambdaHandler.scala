package validation

import cats.data.Validated.*
import cats.effect.IO
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import validation.config.ValidationConfig.prepareValidationConfiguration
import validation.datalaoader.CSVLoader.loadCSVData
import validation.jsonschema.JsonSchemaValidated.*
import validation.jsonschema.ValidatedSchema.{CSVValidationResult, validateRequiredSchema}

object CSVFileValidationLambdaHandler extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {


  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    // TODO get parameters from input
    val jsonConfigFileName = "DaBase.json"
    val altKey = "tdrFileHeader"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, List(jsonConfigFileName, jsonConfigFileName), Some(altKey), "sample.csv", Some(idKey), Some(jsonConfigFileName))


    import cats.effect.unsafe.implicits.*

    csvFileValidation(params).unsafeRunSync() match
      case Valid(data) =>
        val response = new APIGatewayProxyResponseEvent()
        // TODO Add ValidationResult as JSON to body
        response.setStatusCode(200)
        response
      case Invalid(error) =>
        val response = new APIGatewayProxyResponseEvent()
        response.setStatusCode(500)
        response
  }

  def csvFileValidation(parameters: Parameters): IO[CSVValidationResult[List[RowData]]] = {
    for {
      configuration <- prepareValidationConfiguration(parameters)
      data <- IO(
        loadCSVData(configuration.fileToValidate,configuration.idKey)
          andThen addJsonToData(configuration.altToProperty, configuration.valueMapper)
          andThen validateRequiredSchema(configuration.requiredSchema)
      )
      validation <- validateWithMultipleSchema(data, configuration.schema)
    } yield validation
  }
}

