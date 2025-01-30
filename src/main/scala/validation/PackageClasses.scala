package validation

case class RowData(row_number: Option[Int], assetId: Option[String], data: Map[String, Any], json: Option[String] = None)

case class ValidatorConfiguration(altToProperty: String => String, propertyToAlt: String => String, valueMapper: (String, String) => Any)

// Comes from arguments
case class Parameters(configFile: String,
                      schema: List[String],
                      alternateKey: Option[String],
                      fileToValidate: String,
                      idKey: Option[String] = None,
                      requiredSchema: Option[String] = None)
case class ConfigParameters(csConfig: String, alternates: Option[String],keyToOutAlternate: Option[String] = None)


