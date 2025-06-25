package examples

import cats.data.{NonEmptyList, Validated}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSV
import validation.error.ValidationErrors
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, composeMultipleValidated, mapKeys}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.{Parameters, RowData}
import org.apache.pekko.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}


object PekkoStreamExample {

  /**
   * Streaming with Validated
   */
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("CsvStreamSystem")
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val materializer: Materializer = Materializer(system)

    val parameters = Parameters("config.json",
      List("organisationBase.json",
        "openRecord.json",
        "closedRecord.json"),
      Some("TDRMetadataUpload"), "sample.csv", Some("Filepath"), Some("organisationBase.json"), Some("TDRMetadataUpload"))


    val configuration = prepareValidationConfiguration(parameters.configFile, parameters.inputAlternateKey)
    // Load the CSV data and generate 10,000 rows for each row in the CSV to simulate a large dataset
    val smallDataSet: List[RowData] = loadCSV(parameters.fileToValidate, parameters.idKey)
    val largeDataSet: List[RowData] = smallDataSet.zipWithIndex.flatMap { case (row, index) =>
      (1 to 1000).map(i => row.copy(assetId = Some(s"${index * 1000 + i}_${row.assetId.getOrElse("")}")))
    }

    val data =  largeDataSet
    val csvSource = Source(data)

    val startTime = System.currentTimeMillis
    val processedData: Future[Validated[NonEmptyList[ValidationErrors], List[RowData]]] = csvSource
      .map(row => mapKeys(configuration.altInToKey)(List(row)))
      .map(row => row andThen addJsonForValidation(configuration.valueMapper))
      .map(row => row andThen validateSchemaSingleRow(parameters.requiredSchema, configuration.inputAlternateKey))
      .mapAsync(20)(row => Future(row andThen composeMultipleValidated(parameters.schema, configuration.inputAlternateKey)))
      .runFold(Validated.valid[NonEmptyList[ValidationErrors], List[RowData]](List.empty)) { (acc, current) =>
        acc combine current
      }

    processedData.map {
        case Validated.Valid(data) =>
          println(s"Valid in ${System.currentTimeMillis() - startTime} milliseconds")
        case Validated.Invalid(errors) =>
          println(s"Invalid in ${System.currentTimeMillis() - startTime} milliseconds with ${errors.length} errors")
      }
      .onComplete(_ => system.terminate())(system.dispatcher)
  }

}
