package validation

import cats.data.ValidatedNel
import com.networknt.schema.ValidationMessage
import validation.error.{JsonSchemaValidationError, ValidationErrors}

type DataValidationResult[A] = ValidatedNel[ValidationErrors, A]
case class RowData(row_number: Option[Int], assetId: Option[String], data: Map[String, Any], json: Option[String] = None)

case class ValidatorConfiguration(altInToKey: String => String, inputAlternateKey: String => String, valueMapper: (String, String) => Any)

case class Parameters(configFile: String, baseSchema: String, schema: List[String], inputAlternateKey: Option[String], fileToValidate: String, idKey: Option[String] = None, requiredSchema: Option[String] = None, keyToOutAlternate: Option[String] = None)

case class SchemaValidationErrors(assetId: Option[String], errors: Set[ValidationMessage], data: Map[String, Any])
case class ConvertedErrors(assetId: Option[String], errors: Set[JsonSchemaValidationError])




