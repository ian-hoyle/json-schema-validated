package examples

import cats.data.{NonEmptyList, Validated}
import cats.effect.IO
import cats.implicits.*
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSV
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.*
import validation.error.ValidationErrors
import validation.jsonschema.ValidationDataUtils.{addJsonForValidation, mapKeys}
import validation.jsonschema.ValidatedSchema
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.{DataValidation, Parameters, Data}

import scala.concurrent.{ExecutionContext, Future}

object PekkoStreamExample {

  /** Streaming with Validated
    */
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem                = ActorSystem("CsvStreamSystem")
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val materializer: Materializer         = Materializer(system)

    val parameters = Parameters(
      "config.json",
      "organisationBase.json",
      List("organisationBase.json", "openRecord.json", "closedRecord.json"),
      "sample.csv",
      Some("Filepath"),
      Some("organisationBase.json"),
      Some("TDRMetadataUpload")
    )

    val configuration = prepareValidationConfiguration(
      parameters.configFile,
      parameters.baseSchema
    )
    // Load the CSV data and generate 10,000 rows for each row in the CSV to simulate a large dataset
    val smallDataSet: List[Data] = loadCSV(parameters.fileToValidate, parameters.idKey)
    val largeDataSet: List[Data] = smallDataSet.zipWithIndex.flatMap { case (row, index) =>
      (1 to 10000).map(i => row.copy(assetId = Some(s"${index * 10000 + i}_${row.assetId.getOrElse("")}")))
    }

    val data      = largeDataSet
    val csvSource = Source(data)

    val startTime = System.currentTimeMillis
    val processedData: Future[Validated[NonEmptyList[ValidationErrors], List[Data]]] = csvSource
      .map(row => mapKeys(configuration.domainKeyToProperty("TDRMetadataUpload"))(List(row)))
      .map(row => row andThen addJsonForValidation(configuration.valueMapper))
      .map(row =>
        row andThen validateSchemaSingleRow(
          parameters.requiredSchema,
          configuration.propertyToDomainKey("TDRMetadataUpload")
        )
      )
      .mapAsync(20)(row =>
        Future(
          row andThen composeMultipleValidated(parameters.schema, configuration.propertyToDomainKey("TDRMetadataUpload"))
        )
      )
      .runFold(Validated.valid[NonEmptyList[ValidationErrors], List[Data]](List.empty)) { (acc, current) =>
        acc combine current
      }

    processedData
      .map {
        case Validated.Valid(data) =>
          println(s"Valid in ${System.currentTimeMillis() - startTime} milliseconds")
        case Validated.Invalid(errors) =>
          println(
            s"Invalid in ${System.currentTimeMillis() - startTime} milliseconds with ${errors.length} errors"
          )
      }
      .onComplete(_ => system.terminate())(system.dispatcher)
  }

  private def composeMultipleValidated(schemaFiles: List[String], propertyToAlt: String => String)(
      data: List[Data]
  ): DataValidation = {
    schemaFiles.map { schemaFile =>
      ValidatedSchema.schemaValidated(schemaFile, propertyToAlt)(data)
    }.combineAll
  }

}
