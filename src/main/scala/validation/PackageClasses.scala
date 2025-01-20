package validation

case class RowData(row_number: Option[Int], assetId: Option[String], data: Map[String, Any], json: Option[String] = None)

case class ValidatorConfiguration(altToProperty: String => String,
                                  propertyToAlt: String => String,
                                  valueMapper: (property: String,value:String) => Any,
                                  fileToValidate: String,
                                  idKey: Option[String],
                                  requiredSchema: Option[String],
                                  schema: List[String]
                                 )

// Comes from arguments
case class Parameters(csConfig: String,
                      schema: List[String],
                      alternates: Option[String],
                      fileToValidate: String,
                      idKey: Option[String] = None,
                      requiredSchema: Option[String] = None)

