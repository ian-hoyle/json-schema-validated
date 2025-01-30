package validation

import cats.data.Validated.*
import cats.effect.IO
import cats.effect.unsafe.implicits.*
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import validation.config.ValidationConfig.prepareValidationConfiguration
import validation.datalaoader.CSVLoader.loadCSVData
import validation.jsonschema.JsonSchemaValidated.*
import validation.jsonschema.ValidatedSchema.{DataValidationResult, validateRequiredSchema}

object CSVFileValidationLambdaHandler extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {


  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    // TODO get parameters from input
    val jsonConfigFileName = "DaBase.json"
    val altKey = Some("tdrFileHeader")
    val idKey = Some("Filepath")
    val listOfValidationSchema = List(jsonConfigFileName, "open.json")
    val requiredSchema = Some("required.json")

    val params = Parameters(jsonConfigFileName, listOfValidationSchema, altKey, "sample.csv", idKey, requiredSchema,altKey)


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

  def csvFileValidation(parameters: Parameters): IO[DataValidationResult[List[RowData]]] = {
    for {
      configuration <- prepareValidationConfiguration(parameters.configFile,parameters.alternateKey,parameters.keyToOutAlternate)
      data <- IO(
        loadCSVData(parameters.fileToValidate, parameters.idKey)
          andThen addJsonToData(configuration.altToProperty, configuration.valueMapper)
          andThen validateRequiredSchema(parameters.requiredSchema,configuration.propertyToAlt)
      )
      validation <- validateWithMultipleSchema(data, parameters.schema,configuration.propertyToAlt)
    } yield validation
  }
}

