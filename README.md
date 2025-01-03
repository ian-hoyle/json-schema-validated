# mycsvvalidator

## Overview

`mycsvvalidator` is a Scala-based library for validating CSV files. It ensures data integrity by performing various checks and validations on CSV data.

## Features

- UTF-8 validation
- Schema validation using JSON schema
- Customizable configuration for alternate key mappings and value transformations

## Installation

To include `mycsvvalidator` in your project, add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "com.example" %% "mycsvvalidator" % "1.0.0"# mycsvvalidator
## Usage

Here's an example of how to use `mycsvvalidator`:

```scala
import validation.CSVValidator
import validation.CSVValidator.Parameters

val parameters = Parameters(
  csConfig = "config/path",
  schema = List("schema1", "schema2"),
  alternates = Some("alternateKeys"),
  csvFile = "path/to/csvfile.csv",
  idKey = Some("id"),
  requiredSchema = Some("requiredSchema")
)

val validationResult = CSVValidator.validationProgram(parameters).unsafeRunSync()

## AWS Lambda Usage

You can use `mycsvvalidator` in an AWS Lambda function by creating a handler class. Here is an example of `MyLambdaHandler`:

```scala
import cats.data.Validated.*
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import csv.RowData
import validation.jsonschema.ValidatedSchema.CSVValidationResult
import validation.{CSVValidator, Parameters}

class MyLambdaHandler extends RequestHandler[APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent] {

  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    // TODO get parameters from input
    val jsonConfigFileName = "DaBase.json"
    val altKey = "tdrFileHeader"
    val idKey = "Filepath"
    val params = Parameters(jsonConfigFileName, List(jsonConfigFileName, jsonConfigFileName), Some(altKey), "sample.csv", Some(idKey), Some(jsonConfigFileName))

    import cats.effect.unsafe.implicits.*

    val runMe: CSVValidationResult[List[RowData]] = CSVValidator.validationProgram(params).unsafeRunSync()

    runMe match
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
