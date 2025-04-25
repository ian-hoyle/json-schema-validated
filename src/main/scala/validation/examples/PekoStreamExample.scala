package validation.examples

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.*
import validation.config.ValidationConfig.prepareValidationConfiguration
import validation.datalaoader.CSVLoader.loadCSV
import validation.jsonschema.JsonSchemaValidated.{addJsonForValidation, composeMultipleValidated, mapKeys}
import validation.jsonschema.ValidatedSchema.validateSchemaSingleRow
import validation.{Parameters, RowData}


object PekoStreamExample {

  /**
   * Example usage with some sample validations
   */
  def main(args: Array[String]): Unit = {

    val parameters = Parameters("config.json",
      List("organisationBase.json",
        "openRecord.json",
        "closedRecord.json"),
      Some("TDRMetadataUpload"), "sample.csv", Some("Filepath"), None, Some("TDRMetadataUpload"))


    val configuration = prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)
    val data: List[RowData] = loadCSV(parameters.fileToValidate, parameters.idKey)


    // Create the source
    val csvSource = createSourceFromList(data)


    val processedData = csvSource
      .map(row => mapKeys(configuration.altInToKey)(List(row)))
      .map(row => row andThen addJsonForValidation(configuration.valueMapper))
      .map(row => row andThen validateSchemaSingleRow(parameters.requiredSchema, (x: String) => x))
      .map(row => row andThen composeMultipleValidated(parameters.schema, configuration.keyToAltIn))

    // Run the stream and print results
    import org.apache.pekko.actor.ActorSystem
    import org.apache.pekko.stream.Materializer

    implicit val system: ActorSystem = ActorSystem("CsvStreamSystem")
    implicit val materializer: Materializer = Materializer(system)

    processedData
      .runForeach(println)
      .onComplete(_ => system.terminate())(system.dispatcher)
  }

  /**
   * Creates a Peko Source from a List of CSVDataRow objects
   */
  private def createSourceFromList(data: List[RowData]): Source[RowData, NotUsed] = {
    Source(data)
  }

}
