import cats.data.Validated.*
import cats.effect.IO
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import csv.CSVUtils.csvFileValidations
import csv.RowData
import validation.jsonschema.ValidatedSchema.CSVValidationResult
import validation.{JsonSchemaValidated, Parameters}
import validation.JsonSchemaValidated.*

class CSVFileValidationLambdaHandler extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {


  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    // TODO get parameters from input
    val jsonConfigFileName = "DaBase.json"
    val altKey = "tdrFileHeader"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, List(jsonConfigFileName, jsonConfigFileName), Some(altKey), "sample.csv", Some(idKey), Some(jsonConfigFileName))

    import cats.effect.unsafe.implicits.*
    
    def validationProgram(parameters: Parameters): IO[CSVValidationResult[List[RowData]]] = {
      for {
        configuration <- prepareCSVConfiguration(parameters)
        data <- csvFileValidations(configuration)
        validation <- dataValidation(data, configuration.schema)
      } yield validation
    }

    validationProgram(params).unsafeRunSync() match
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
}

