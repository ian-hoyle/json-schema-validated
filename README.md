# json-schema-validated

A data validation library for Scala that leverages JSON Schema for schema-based validation and uses [Cats Data Validated](https://typelevel.org/cats/datatypes/validated.html) for functional error accumulation and reporting.

## Table of Contents
- [Justification](#justification)
- [Overview](#overview)
- [Schema](#schema)
- [Example usage](#example-usage)

## Justification
Needed to support the transfer of data to The National Archives (TNA)
  - The data could be in different formats (CSV, JSON, etc.)
  - The data must be validated so that it can be used in the TNA system
  - The data schema must be defined in a central location 
  - The schema should be defined once but should be allowed to be used for different domains 
  - Multiple schemas should be allowed to be used for the same data
  - Error messages should be user-friendly and easy to understand
  - The schema and mapping to domain help pages should be generated from the schema
  - Custom validations should be allowed and easy to define

JSON schema can be used to define the data schema

  - **The validation should be done in a way that is easy to understand and maintain**
 
Scala can be used to satisfy this requirement

## Overview

`json-schema-validated` is a Scala-based data validation library for validating data using JSON Schema and functional programming principles. It loads data as key-value pairs, supports multiple domains, and validates using [NetworkNT JSON Schema Validator](http://github.com/networknt/json-schema-validator) and [Cats Data Validated](https://typelevel.org/cats/datatypes/validated.html) for robust error handling and composability.

The JSON schema validation is performed using [NetworkNT JSON Schema Validator](http://github.com/networknt/json-schema-validator)

Conditional validation is supported by the use of multiple schema

Custom user friendly messages are supported by the use of properties files with similar names as the schema

## Schema
Two fundamental schema are used in the validation process 
- [The base JSON schema](src/main/resources/organisationBase.json) defining all data and their types.
- [Configuration JSON schema](src/main/resources/config.json) that can be used to [define](src/main/scala/validation/PackageClasses.scala#L15)
  - Alternate keys for mapping between domains
  - Domain specific validations that can't be defined using JSON schema

Here is a snippet from `organisationBase.json`:

```json
{
  "$id": "/schema/baseSchema",
  "type": "object",
  "properties": {
    "foi_exemption_code": {
      "type": [
        "array",
        "null"
      ],
      "items": {
        "type": "string",
        "$ref": "classpath:/definitions.json#/definitions/foi_codes"
      }
    },
    "file_size": {
      "type": "integer",
      "minimum": 1
    },
    "UUID": {
      "type": "string",
      "format": "uuid"
    },
    "file_path": {
      "type": "string",
      "minLength": 1
    }
  },
  "additionalProperties": {
    "description": "Additional properties would be defined here"
  }
}
```

Here is a snippet from `config.json`:

```json
{
  "$id": "/schema/config",
  "type": "object",
  "configItems": [
    {
      "key": "file_path",
      "domainKeys": [
        { "domain": "TDRMetadataUpload", "domainKey": "Filepath" },
        { "domain": "TDRDataLoad", "domainKey": "Filepath" }
      ]
    },
    {
      "key": "foi_exemption_code",
      "domainKeys": [
        { "domain": "TDRMetadataUpload", "domainKey": "FOI exemption code" }
      ]
    },
    {
      "key": "description",
      "domainKeys": [
        { "domain": "TDRMetadataUpload", "domainKey": "Description" }
      ]
    }
    // ... more config items ...
  ]
}
```

One or many JSON schema can be used to validate the data with error messages defined for each schema.

Here is a snippet from `closedRecord.json` schema that could also be applied:

```json
{
  "$id": "/schema/closed-closure",
  "type": "object",
  "allOf": [
    {
      "if": {
        "properties": {
          "closure_type": { "const": "Closed" }
        }
      },
      "then": {
        "properties": {
          "closure_start_date": { "type": "string" },
          "closure_period": { "type": "integer" },
          "foi_exemption_code": { "type": "array" },
          "foi_exemption_asserted": { "type": "string" },
          "title_closed": { "type": "boolean" },
          "description_closed": { "type": "boolean" }
        }
        // ... more conditional logic ...
      }
    }
    // ... more conditions ...
  ]
}
```

**How conditional validation works:**

This schema uses the `if` and `then` keywords to apply additional validation rules only when the `closure_type` property is set to `"Closed"`. If `closure_type` is not `"Closed"`, these extra requirements are not enforced. This allows the schema to dynamically require or validate fields based on the value of another field, supporting context-sensitive validation logic.

## Example usage

The main API for validation is the `validate` method in the [`Validation`](src/main/scala/validation/Validation.scala) object. This method allows you to apply a sequence of fail-fast validations (which stop at the first failure) and a sequence of composed validations (which are run in parallel and whose results are combined).

### The `validate` method signature

```scala
def validate(
  dataLoader: ValidatedNel[ValidationErrors, List[RowData]],
  failFastValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]],
  composeValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]]
): DataValidationResult[List[RowData]]
```

- **dataLoader**: The initial data to validate, typically loaded and parsed from a file, wrapped in a `ValidatedNel`.
- **failFastValidations**: A sequence of validations that are applied in order; if any fail, validation stops immediately.
- **composeValidations**: A sequence of validations that are run after the fail-fast validations, in parallel, and their results are combined.

> **Note:**
> `DataValidationResult[List[RowData]]` is a type alias for `ValidatedNel[ValidationErrors, List[RowData]]`.

### Example usage

Below is a simplified example of how to use the `validate` API:

```scala
import validation.Validation.validate
import validation.{DataValidationResult, RowData, ValidationErrors}
import cats.data.ValidatedNel

// Assume you have a dataLoader that loads your data as a ValidatedNel
val dataLoader: ValidatedNel[ValidationErrors, List[RowData]] = ...

// Define your fail-fast and composed validations
val failFastValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]] = Seq(
  // e.g. mapping keys, adding JSON, required field checks
)
val composeValidations: Seq[List[RowData] => DataValidationResult[List[RowData]]] = Seq(
  // e.g. schema validations, business rule checks
)

// Run validation
val result = validate(dataLoader, failFastValidations, composeValidations)

result match {
  case cats.data.Validated.Valid(data) =>
    println("Validation successful")
  case cats.data.Validated.Invalid(errors) =>
    println(s"Validation failed with ${errors.length} errors")
}
```

This approach allows you to flexibly compose different validation steps, separating those that should stop processing immediately from those that can be accumulated and reported together.

For an example, see [`CSVFileValidationApp`](src/main/scala/examples/CSVFileValidationApp.scala).


```scala
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSVData
import validation.Validation.validate
import validation.jsonschema.ValidatedSchema
import validation.{DataValidationResult, Parameters, RowData, ValidatorConfiguration}

object CSVFileValidationApp extends App {
  val parameters = Parameters(
    configFile = "config.json",
    baseSchema = "organisationBase.json",
    schema = List("organisationBase.json", "openRecord.json", "closedRecord.json"),
    inputAlternateKey = Some("TDRMetadataUpload"),
    fileToValidate = "sample.csv",
    idKey = Some("Filepath"),
    requiredSchema = None,
    keyToOutAlternate = Some("TDRMetadataUpload")
  )

  val configuration: ValidatorConfiguration = prepareValidationConfiguration(
    parameters.configFile,
    parameters.baseSchema,
    parameters.inputAlternateKey
  )

  val dataLoader: DataValidationResult[List[RowData]] = loadCSVData(parameters.fileToValidate, parameters.idKey)

  val failFastValidations = List(
    // Add fail-fast validations here, e.g. mapping keys, adding JSON, etc.
  )
  val combiningValidations = ValidatedSchema.generateSchemaValidatedList(parameters.schema, configuration.inputAlternateKey)

  val result = validate(dataLoader, failFastValidations, combiningValidations)

  result match {
    case cats.data.Validated.Valid(data) =>
      println("Validation successful")
    case cats.data.Validated.Invalid(errors) =>
      println(s"Validation failed with ${errors.length} errors")
  }
}
```

This example demonstrates how to configure, load, and validate CSV data using multiple JSON schemas. For more details, see the [examples](src/main/scala/examples) directory.

### Configuration Loading

The `prepareValidationConfiguration` method in the [ValidationConfig](src/main/scala/config/ValidationConfig.scala) object is responsible for preparing the validation configuration using the provided config file, base schema, and alternate key. Here is the current method signature and a brief explanation:

```scala
def prepareValidationConfiguration(
  configFile: String,
  baseSchema: String,
  alternateKey: Option[String]
): ValidatorConfiguration = {
  val csvConfigurationReader = for {
    altHeaderToPropertyMapper <- Reader(ValidationConfig.domainKeyToPropertyMapper)
    propertyToAltHeaderMapper <- Reader(ValidationConfig.propertyToDomainKeyMapper)
    valueMapper <- Reader(ValidationConfig.stringValueMapper)
  } yield ValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper, valueMapper)
  csvConfigurationReader.run(ConfigParameters(configFile, alternateKey, baseSchema, decodeConfig(configFile)))
}
```

- **configFile**: Path to the configuration file.
- **baseSchema**: Path to the base JSON schema file.
- **alternateKey**: Optional alternate key for domain mapping.
- **ValidatorConfiguration**: Combines the above functions into a `ValidatorConfiguration` object.

The method returns a `ValidatorConfiguration` object.

### generateSchema Task

The `generateSchema` task is an SBT custom task designed to generate JSON schemas based on provided configurations. This task reads a list of JSON files, processes them, and outputs the modified schemas.

The schema generated are copies with the original property names replaced with the alternate keys defined in the configuration file. 

The task is defined in the [GenerateSchema](project/GenerateSchema.scala) object.

### Usage

To use the `generateSchema` task, follow these steps:

1. **Configure the list of file names**:
   Set the list of JSON file names that you want to process using the `fileNames` setting key.

2. **Run the task**:
   Execute the `sbt generateSchema` task from the console.
