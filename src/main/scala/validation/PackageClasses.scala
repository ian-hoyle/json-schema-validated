package validation

import cats.data.ValidatedNel
import com.networknt.schema.ValidationMessage
import validation.error.{JsonSchemaValidationError, ValidationErrors}

type DataValidation = ValidatedNel[ValidationErrors, List[Data]]
case class Data(
    row_number: Option[Int],
    assetId: Option[String],
    data: Map[String, Any],
    json: Option[String] = None
)

case class ValidatorConfiguration(
    domainKeyToProperty: String => String => String,
    propertyToDomainKey: String => String => String,
    valueMapper: (String, String) => Any
)

case class Parameters(
    configFile: String,
    baseSchema: String,
    schema: List[String],
    fileToValidate: String,
    idKey: Option[String] = None,
    requiredSchema: Option[String] = None,
    keyToOutAlternate: Option[String] = None
)

case class SchemaValidationErrors(
    assetId: Option[String],
    errors: Set[ValidationMessage],
    data: Map[String, Any]
)
case class ConvertedErrors(assetId: Option[String], errors: Set[JsonSchemaValidationError])
