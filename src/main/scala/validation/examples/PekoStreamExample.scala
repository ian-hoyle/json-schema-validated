package validation.examples

import cats.data.{NonEmptyList, Validated}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import validation.config.ValidationConfig.prepareValidationConfiguration
import validation.datalaoader.CSVLoader.loadCSV
import validation.error.ValidationErrors
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, composeMultipleValidated, mapKeys}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.{Parameters, RowData}

import scala.concurrent.{ExecutionContext, Future}


object PekoStreamExample {

  /**
   * Example usage with some sample validations
   */
  def main(args: Array[String]): Unit = {

    val parameters = Parameters("config.json",
      List("organisationBase.json",
        "openRecord.json",
        "closedRecord.json"),
      Some("TDRMetadataUpload"), "sample.csv", Some("Filepath"), Some("organisationBase.json"), Some("TDRMetadataUpload"))


    val configuration = prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)
    // Load the CSV data and generate 10,000 rows for each row in the CSV to simulate a large dataset
    val data: List[RowData] = loadCSV(parameters.fileToValidate, parameters.idKey).zipWithIndex.flatMap { case (row, index) =>
      (1 to 10000).map(i => row.copy(assetId = Some(s"${index * 10000 + i}_${row.assetId.getOrElse("")}")))
    }


    // Create the source
    val csvSource = Source(data)
    implicit val system: ActorSystem = ActorSystem("CsvStreamSystem")
    implicit val executionContext: ExecutionContext = system.dispatcher
    import org.apache.pekko.stream.Materializer
    implicit val materializer: Materializer = Materializer(system)

    val startTime = System.currentTimeMillis
    val processedData: Future[Validated[NonEmptyList[ValidationErrors], List[RowData]]] = csvSource
      .map(row => mapKeys(configuration.altInToKey)(List(row)))
      .map(row => row andThen addJsonForValidation(configuration.valueMapper))
      .map(row => row andThen validateSchemaSingleRow(parameters.requiredSchema, configuration.keyToAltIn))
      .mapAsync(20)(row => Future(row andThen composeMultipleValidated(parameters.schema, configuration.keyToAltIn)))
      .runFold(Validated.valid[NonEmptyList[ValidationErrors], List[RowData]](List.empty)) { (acc, current) =>
        acc combine current
      }

    processedData.map {
        case Validated.Valid(data) => //println(data)
        case Validated.Invalid(errors) => println(s"${System.currentTimeMillis() - startTime}") //errors.toList.foreach(println)
      }
      .onComplete(_ => system.terminate())(system.dispatcher)
  }

  /**
   * Creates a Peko Source from a List of CSVDataRow objects
   */
  //  def createSourceFromList(data: List[RowData]): Source[RowData, NotUsed] = {
  //    Source(data)
  //  }

}
