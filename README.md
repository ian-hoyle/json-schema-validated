# json-schema-validated

A data validation library for Scala that leverages JSON Schema for schema-based validation and uses [Cats Data Validated](https://typelevel.org/cats/datatypes/validated.html) for functional error accumulation and reporting.

## Features
- Validate data against one or many JSON Schemas (NetworkNT JSON Schema Validator)
- Compose validations as functions: fail-fast chains and parallel error-accumulating checks
- Map between domain-specific headers and canonical property names via config
- User-friendly, per-schema error messages using .properties files
- Optional conditional rules with JSON Schema if/then
- SBT task to generate case classes
- Examples for CSV data, including a Lambda-friendly handler

## Table of Contents
- [Justification](#justification)
- [How it works (60 seconds)](#how-it-works-60-seconds)
- [Architecture](#architecture)
- [Overview](#overview)
- [Schema](#schema)
- [User-Friendly Error Messages](#user-friendly-error-messages)
- [Example usage](#example-usage)
- [Small example: input and output](#small-example-input-and-output)
- [Configuration Loading](#configuration-loading)
- [Quick start](#quick-start)

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

### Validation as composable function lists

- Each validation is a function of the form `List[Data] => DataValidation` where `DataValidation = ValidatedNel[ValidationErrors, List[Data]]`.
- You typically organise validations into two pipelines:
  - Fail-fast sequence: run steps in order, short-circuiting on the first failure (using `andThen`).
  - Parallel/accumulating: run independent validations over the same input and accumulate all errors into a single `ValidatedNel`.

Conceptually:

```scala
import cats.data.ValidatedNel
import cats.syntax.all._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import validation.error.CSVValidationResult.dataValidationResultMonoid

// type DataValidation = ValidatedNel[ValidationErrors, List[Data]]

def runFailFast(
  loadedData: DataValidation,
  steps: Seq[List[Data] => DataValidation]
): DataValidation =
  steps.foldLeft(loadedData)((acc, f) => acc.andThen(f))

def runComposed(
  data: List[Data],
  steps: Seq[List[Data] => DataValidation]
): DataValidation =
  // run validations in parallel and combine their Validated results (accumulating errors)
  // NOTE: each schema validation converts raw validator messages into user-friendly
  // JsonSchemaValidationError using schema .properties (see ValidatedSchema.schemaValidated)
  steps.map(f => IO(f(data))).parSequence.map(_.combineAll).unsafeRunSync()

val result: DataValidation =
  runFailFast(dataLoader, failFastValidations).andThen(ds => runComposed(ds, composeValidations))
```

This mirrors Validation.scala: fail-fast via `andThen`, then parallel composition with IO and `combineAll`. Crucially, each schema validation (e.g. `ValidatedSchema.schemaValidated`) performs the error-to-message mapping using the schema’s `.properties` file; the Monoid then simply combines these already user-friendly errors.

## How it works (60 seconds)
1. Load data as a `ValidatedNel` of `List[Data]` (e.g. from CSV).
2. Apply fail-fast validations (e.g. header mapping, JSON enrichment) via `andThen`.
3. Apply parallel/composed validations (e.g. multiple JSON Schemas, business rules) and accumulate errors.
4. Error messages are mapped to user-friendly text inside each schema validation using the schema’s `.properties`; the Monoid only combines already-mapped errors.
5. Return either validated data or an accumulated non-empty list of errors, grouped per asset.

## Architecture

```
           +------------------+
           |   Source files   |  (CSV, JSON, ...)
           +---------+--------+
                     |
                     v
            +--------+---------+
            |     Data loader  |  (parses to List[Data])
            +--------+---------+
                     |
                     v
         +-----------+--------------------+
         |  Fail-fast validations (andThen)|  e.g. key mapping, JSON enrichment
         +-----------+--------------------+
                     |
                     v
         +-----------+----------------------------------------------+
         |  Parallel/composed validations                           |
         |  - JSON Schema + business rules                          |
         |  - Error mapping to user-friendly messages (per schema)  |
         +-----------+----------------------------------------------+
                     |
                     v
                 Validated result
```

## Overview

`json-schema-validated` is a Scala-based data validation library for validating data using JSON Schema and functional programming principles. It loads data as key-value pairs, supports multiple domains, and validates using [NetworkNT JSON Schema Validator](http://github.com/networknt/json-schema-validator) and [Cats Data Validated](https://typelevel.org/cats/datatypes/validated.html) for robust error handling and composability.

The JSON schema validation is performed using [NetworkNT JSON Schema Validator](http://github.com/networknt/json-schema-validator)

Conditional validation is supported by the use of multiple schemas

Custom user-friendly messages are supported by the use of properties files with similar names as the schema

## Schema
Two fundamental schemas are used in the validation process 
- [The base JSON schema](src/main/resources/organisationBase.json) defining all data and their types.
- [Configuration JSON schema](src/main/resources/config.json) that can be used to [define](src/main/scala/validation/PackageClasses.scala)
  - Alternate keys for mapping between domains
  - Domain-specific validations that can't be defined using JSON schema

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
  ]
}
```

One or many JSON schemas can be used to validate the data with error messages defined for each schema.

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
      }
    }
  ]
}
```

**How conditional validation works:**

This schema uses the `if` and `then` keywords to apply additional validation rules only when the `closure_type` property is set to `"Closed"`. If `closure_type` is not `"Closed"`, these extra requirements are not enforced. This allows the schema to dynamically require or validate fields based on the value of another field, supporting context-sensitive validation logic.

## User-Friendly Error Messages

For each JSON schema file, there is a corresponding `.properties` file with the same base name that contains user-friendly error messages. The key in the properties file is formatted as `{property}.{errorKey}`, matching the property and errorKey from validation errors.

During validation, `ValidatedSchema.schemaValidated` converts raw validator messages (`ValidationMessage`) into `JsonSchemaValidationError` by looking up the user-friendly text in the schema’s `.properties`. This happens inside each schema validation step, so when results are combined, the errors are already mapped to friendly messages.

For example, with `closedRecord.json` schema:

```json
{
  "$id": "/schema/closed-closure",
  "type": "object",
  "allOf": [
    {
      "if": {
        "properties": {
          "closure_type": {
            "const": "Closed"
          }
        }
      },
      "then": {
        "properties": {
          "closure_start_date": {
            "type": "string"
          },
          "foi_exemption_code": {
            "type": "array"
          },
          "foi_exemption_asserted": {
            "type": "string"
          }
        },
        "allOf": [
          {
            "if": {
              "properties": {
                "title_closed": { "const": true }
              }
            },
            "required": ["title_closed"],
            "then": {
              "required": ["title_alternate"],
              "properties": {
                "title_alternate": {
                  "type": "string"
                }
              }
            }
          }
        ]
      }
    }
  ]
}
```

The corresponding `closedRecord.properties` file contains user-friendly error messages:

```properties
foi_exemption_asserted.type=Must be provided for a closed record
foi_exemption_code.type=Must be provided for a closed record
closure_period.type=Must be provided for a closed record
closure_start_date.type=Must be provided for a closed record
title_closed.type=Must be provided for a closed record
title_closed.enum=Must be Yes or No
title_closed.const=Must be Yes if an alternate is provided
title_alternate.type=Must not be empty if title is closed
description_closed.type=Must be provided for a closed record
description_closed.const=Must be Yes if an alternate is provided
description_closed.enum=Must be Yes or No
description_alternate.type=Must not be empty if description is closed
```

When validation fails, the library looks up the appropriate error message using the property name and error key. For example, if the `title_closed` field fails validation with an error key of `const`, the error message "Must be Yes if an alternate is provided" will be displayed to the user.

This approach allows for customized, context-specific error messages that are more helpful to end users than generic JSON Schema validation errors.

## Example usage

Below is a simplified example of how to use the `validate` API:

```scala
import validation.Validation.validate
import validation.{DataValidation, Data, ValidationErrors}

val dataLoader: DataValidation = ??? // load your data

val failFastValidations: Seq[List[Data] => DataValidation] = Seq(
  // e.g. mapping keys, adding JSON, required field checks
)
val composeValidations: Seq[List[Data] => DataValidation] = Seq(
  // e.g. schema validations, business rule checks
)

val result = validate(dataLoader, failFastValidations, composeValidations)

result match {
  case cats.data.Validated.Valid(data) => println("Validation successful")
  case cats.data.Validated.Invalid(errors) => println(s"Validation failed with ${errors.length} errors")
}
```

A tiny custom validation example:

```scala
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all._

// Ensures every record has a non-empty "file_path"
def requireFilePath: List[Data] => DataValidation = { rows =>
  val missing = rows.filter(r => r.data.get("file_path").forall(_.toString.trim.isEmpty))
  if (missing.isEmpty) rows.validNel
  else {
    val errs = missing.flatMap { r =>
      r.assetId.toList.map(id => ValidationErrors(id, Set(JsonSchemaValidationError(
        validationProcess = "RequiredFields",
        property = "file_path",
        errorKey = "required",
        message = "Must be provided",
        value = ""
      ))))
    }
    // collapse per-asset errors and return all rows as invalid
    Validated.invalidNel(errs.reduce(_ |+| _))
  }
}
```

For a complete example, see [`CSVFileValidationApp`](src/main/scala/examples/CSVFileValidationApp.scala).

```scala
import config.ValidationConfig.prepareValidationConfiguration
import datalaoader.CSVLoader.loadCSVData
import validation.Validation.validate
import validation.jsonschema.ValidatedSchema
import validation.{DataValidation, Parameters, Data, ValidatorConfiguration}

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

  val dataLoader: DataValidation = loadCSVData(parameters.fileToValidate, parameters.idKey)

  val failFastValidations = List(
    // Add fail-fast validations here, e.g. mapping keys, adding JSON, etc.
  )
  val combiningValidations = ValidatedSchema.generateSchemaValidatedList(parameters.schema, configuration.inputAlternateKey)

  val result = validate(dataLoader, failFastValidations, combiningValidations)

  result match {
    case cats.data.Validated.Valid(data) => println("Validation successful")
    case cats.data.Validated.Invalid(errors) => println(s"Validation failed with ${errors.length} errors")
  }
}
```

## Small example: input and output

Input row (conceptual):

```json
{
  "Filepath": "a/b/c.txt",
  "closure_type": "Closed",
  "title_closed": true
}
```

Expected validation outcome (friendly messages):

```
- file a/b/c.txt
  - foi_exemption_asserted.type: Must be provided for a closed record
  - closure_start_date.type: Must be provided for a closed record
  - closure_period.type: Must be provided for a closed record
  - foi_exemption_code.type: Must be provided for a closed record
  - title_alternate.type: Must not be empty if title is closed
```

These messages are resolved via `closedRecord.properties` based on the JSON Schema error keys.

## Configuration Loading

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

### Quick start

- Prerequisites: Java 11+, SBT, Scala 3
- Run tests: `sbt test`
- Generate case classes: `sbt generateCaseClasses`
- Run the CSV example app: `sbt "runMain examples.CSVFileValidationApp"`
- Lambda handler example: see [`CSVFileValidationLambdaHandler`](src/main/scala/examples/CSVFileValidationLambdaHandler.scala)
