# json-schema-validated

![Scala 3](https://img.shields.io/badge/Scala-3-2563eb)
![JSON Schema](https://img.shields.io/badge/JSON%20Schema-2563eb)
![Cats Effect](https://img.shields.io/badge/Cats%20Effect-2563eb)

A powerful Scala data validation library that combines JSON Schema validation with functional programming principles using [Cats Validated](https://typelevel.org/cats/datatypes/validated.html) for robust error accumulation and composable validation pipelines.

| Functional Programming with JSON Schema | Typelevel Cats - Functional Programming Library |
| :-------------------------------------: | :---------------------------------------------: |
|  ![Functional Programming with JSON Schema](pages/img/fp.webp)  | ![Typelevel Cats - Functional Programming Library](pages/img/cats.png) |

---

### 🚀 Quick Start

Get up and running in minutes:

```bash
# Prerequisites: Java 11+, SBT, Scala 3
sbt test                                    # Run tests
echo '{"closure_type":"Closed","file_path":null}' > sample.json
sbt "runMain examples.JsonValidationApp --schemas=organisationBase.json,closedRecord.json --json-file=sample.json"
sbt "runMain examples.CSVFileValidationApp" # Run CSV validation example
```

## Why json-schema-validated?

| 🎯 Schema-Driven Validation | 🔧 Composable Validations | 💬 User-Friendly Errors | 🏗️ Functional Architecture |
| :--- | :--- | :--- | :--- |
| Define your data structure once using JSON Schema and validate across multiple domains and formats. | Build complex validation pipelines with fail-fast and parallel error-accumulating strategies. | Automatic mapping to human-readable error messages using properties files for each schema. | Leverages Cats Validated for type-safe error handling and functional composition patterns. |

## Key Features

*   **Multi-Schema Validation:** Validate data against one or multiple JSON Schemas using NetworkNT JSON Schema Validator
*   **Functional Composition:** Compose validations as functions with fail-fast chains and parallel error-accumulating checks
*   **Domain Mapping:** Map between domain-specific headers and canonical property names via configuration
*   **Smart Error Messages:** User-friendly, per-schema error messages using .properties files
*   **Conditional Validation:** Support for complex business rules using JSON Schema if/then constructs
*   **Code Generation:** SBT task to automatically generate Scala case classes from schemas
*   **Production Ready:** Includes CSV validation examples and AWS Lambda-compatible handlers

## 📖 Table of Contents

*   [Use Case: TNA Data Transfer](#use-case-tna-data-transfer)
*   [How It Works](#how-it-works)
*   [Validation Patterns](#validation-patterns)
*   [Architecture Overview](#architecture-overview)
*   [Schema Structure](#schema-structure)
*   [API Reference](#api-reference)
*   [Configuration](#configuration)
*   [Examples](#examples)
*   [Generated Constants](#generated-constants)
*   [Additional Resources](#additional-resources)

## 🎯 Use Case: TNA Data Transfer

Originally designed to support data transfer to The National Archives (TNA), this library addresses common enterprise validation challenges:

> **✅ What We Solve:**
> *   Multi-format data validation (CSV, JSON, XML)
> *   Centralized schema definition with domain-specific mappings
> *   Composable validation rules for complex business logic
> *   User-friendly error reporting for non-technical users
> *   Automated documentation generation from schemas

### Real-World Example

**Input:** CSV file with metadata for digital archives

| Filepath | Filename | Date last modified | Closure status | Description | Language |
| :--- | :--- | :--- | :--- | :--- | :--- |
| test/test1.txt | test1.txt | 2024-03-26 | Closed | Archive document | English |
| test/test2.txt | test2.txt | 2024-03-26 | OpenX | Invalid status example | English |

**Output:** Structured validation results with actionable error messages

```json
[
  {
    "assetId": "test/test2.txt",
    "errors": [
      {
        "validationProcess": "organisationBase.json",
        "property": "Closure status",
        "errorKey": "enum",
        "message": "Must be either 'Open' or 'Closed'",
        "value": "OpenX"
      }
    ]
  }
]
```

## ⚙️ How It Works

1.  **Data Loading:** Parse input data (CSV, JSON, etc.) into structured `List[Data]`
2.  **Fail-Fast Validation:** Apply sequential validations (header mapping, data enrichment) that stop on first failure
3.  **Parallel Validation:** Run independent schema validations concurrently and accumulate all errors
4.  **Error Transformation:** Map technical validation errors to user-friendly messages using schema properties
5.  **Result Aggregation:** Return either validated data or comprehensive error report grouped by asset

## 🔄 Validation Patterns

> **💡 Key Concept:** Each validation is a function `List[Data] → ValidatedNel[ValidationErrors, List[Data]]`

### Composable Validation Functions

Organize validations into two complementary pipelines:

*   **Fail-Fast Pipeline:** Sequential execution using `andThen` - stops at first error
*   **Parallel Pipeline:** Concurrent execution with error accumulation using `ValidatedNel`

```scala
import cats.data.ValidatedNel
import cats.syntax.all._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import validation.error.CSVValidationResult.dataValidationResultMonoid

// Core type definitions
type DataValidation = ValidatedNel[ValidationErrors, List[Data]]

// Fail-fast validation pipeline
def runFailFast(
  loadedData: DataValidation,
  steps: Seq[List[Data] => DataValidation]
): DataValidation =
  steps.foldLeft(loadedData)((acc, f) => acc.andThen(f))

// Parallel validation with error accumulation
def runComposed(
  data: List[Data],
  steps: Seq[List[Data] => DataValidation]
): DataValidation =
  steps.map(f => IO(f(data))).parSequence.map(_.combineAll).unsafeRunSync()

// Combined validation strategy
val result: DataValidation =
  runFailFast(loadedData, failFastValidations)
    .andThen(ds => runComposed(ds, composeValidations))
```

> **⚠️ Architecture Note:** Error-to-message mapping happens within each schema validation using the schema's `.properties` file. The Monoid simply combines these pre-processed, user-friendly errors.

## 🏗️ Architecture Overview

```
                    📁 Data Sources
                    (CSV, JSON, XML)
                           │
                           ▼
                    🔄 Data Loader
                  (Parse → List[Data])
                           │
                           ▼
              ⚡ Fail-Fast Validations (andThen)
              • Header mapping
              • Data enrichment
              • Required field checks
                           │
                           ▼
              🔀 Parallel Validations (combineAll)
              • JSON Schema validation
              • Business rules
              • Cross-field validation
              • Error → User message mapping
                           │
                           ▼
                    ✅ Validated Result
                  (Success | Error Report)
```

## 📋 Schema Structure

The validation system uses a layered schema approach:

### 1. Base Data Schema

Defines core data types and constraints:

```json
{
  "$id": "/schema/baseSchema",
  "type": "object",
  "properties": {
    "foi_exemption_code": {
      "type": ["array", "null"],
      "items": {
        "type": "string",
        "$ref": "classpath:/definitions.json#/definitions/foi_codes"
      }
    },
    "file_size": {
      "type": "integer",
      "minimum": 1,
      "description": "File size in bytes"
    },
    "file_path": {
      "type": "string",
      "minLength": 1,
      "pattern": "^[^\\0]+$"
    }
  }
}
```

### 2. Conditional Validation Schema

Applies context-sensitive business rules:

```json
{
  "$id": "/schema/closed-record",
  "type": "object",
  "allOf": [
    {
      "if": {
        "properties": {
          "closure_type": { "const": "Closed" }
        },
        "required": ["closure_type"]
      },
      "then": {
        "required": ["closure_start_date", "closure_period"],
        "properties": {
          "closure_start_date": { "type": "string", "format": "date" },
          "closure_period": { "type": "integer", "minimum": 1 },
          "foi_exemption_code": { "type": "array", "minItems": 1 }
        }
      },
      "else": {
        "properties": {
          "closure_start_date": { "type": "null" },
          "closure_period": { "type": "null" }
        }
      }
    }
  ]
}
```

### 3. Domain Mapping Configuration

Maps between canonical properties and domain-specific headers:

```json
{
  "$id": "/schema/config",
  "type": "object",
  "configItems": [
    {
      "key": "file_path",
      "domainKeys": [
        { "domain": "TDRMetadataUpload", "domainKey": "Filepath" },
        { "domain": "TDRDataLoad", "domainKey": "File Location" }
      ]
    },
    {
      "key": "foi_exemption_code",
      "domainKeys": [
        { "domain": "TDRMetadataUpload", "domainKey": "FOI exemption code" }
      ]
    }
  ]
}
```

## 📚 API Reference

### Core Validation Function

```scala
def validate(
  dataLoader: DataValidation,
  failFastValidations: Seq[List[Data] => DataValidation],
  composeValidations: Seq[List[Data] => DataValidation]
): DataValidation
```

### Key Data Types

*   **Data:** Single validation record containing `row_number`, `assetId`, `data` map, and `json` representation
*   **ValidationErrors:** Asset-specific errors with `assetId` and `Set[JsonSchemaValidationError]`
*   **JsonSchemaValidationError:** Detailed error info including `validationProcess`, `property`, `errorKey`, `message`, and `value`

> **💡 Type Safety:** `DataValidation` is a type alias for `ValidatedNel[ValidationErrors, List[Data]]`, providing compile-time guarantees for error handling.

## 💬 User-Friendly Error Messages

Each JSON schema has a corresponding `.properties` file defining human-readable error messages:

| Schema: organisationBase.json |
| :--- |
| **Properties:** organisationBase.properties |
| <pre><code># Format: {property}.{errorKey}={message}<br>closure_type.enum=Must be either 'Open' or 'Closed'<br>file_size.minimum=File size must be at least 1 byte<br>file_path.minLength=File path cannot be empty</code></pre> |

## ⚙️ Configuration Setup

```scala
def prepareValidationConfiguration(
      configFile: String,
      baseSchema: String
  ): ValidatorConfiguration = {

    val csvConfigurationReader = for {
      altHeaderToPropertyMapper <- Reader(domainPropertyToBasePropertyMapper)
      propertyToAltHeaderMapper <- Reader(domainBasePropertyToPropertyMapper)
      valueMapper               <- Reader(stringValueMapper)
    } yield ValidatorConfiguration(
      altHeaderToPropertyMapper,
      propertyToAltHeaderMapper,
      valueMapper
    )
    csvConfigurationReader.run(
      ConfigParameters(configFile, baseSchema, decodeConfig(configFile))
    )
  }
  case class ValidatorConfiguration(
    domainKeyToProperty: String => String => String,
    propertyToDomainKey: String => String => String,
    valueMapper: (String, String) => Any
    )
```

## 🧬 Generated Constants

The build defines two source-generation tasks (implemented as SBT auto plugins) that create type-safe constants you can use instead of raw string literals when referring to schema property names or domain identifiers. These are generated on every `compile` (they live under `target/…/src_managed`).

> **Why?** Eliminates hard‑coded strings, reduces typo risk, and makes refactors (schema / domain changes) safer.

### 1. Base Schema Property Constants

*   **Plugin:** `BaseSchemaGeneratorPlugin`
*   **Input:** `src/main/resources/organisationBase.json` (top-level `properties` section)
*   **Output file:** `target/scala-*/src_managed/main/generated/BaseSchema.scala`
*   **Object:** `validation.generated.BaseSchema`
*   **Contents:** One `final val` per JSON property (e.g. `file_path`, `foi_exemption_code`, `closure_type`)

### 2. Config Domain Constants

*   **Plugin:** `DomainKeysGeneratorPlugin`
*   **Input:** `src/main/resources/config.json` (reads `configItems[].domainKeys[].domain`)
*   **Output file:** `target/scala-*/src_managed/main/generated/ConfigDomains.scala`
*   **Object:** `validation.generated.ConfigDomains`
*   **Contents:** Distinct domains (e.g. `TDRMetadataUpload`, `TDRDataLoad`) as `final val` constants.

## 📋 Additional Resources

| 🗺️ Schema Domain Mapping | 📋 Metadata Template |
| :--- | :--- |
| Interactive visualization of property mappings across domains | TNA metadata template rendered as user-friendly reference table |
| [View Mapping Tool →](pages/schema-domain-map.html) | [View Template →](pages/metadata-template.html) |

---

> **🔗 External Dependencies:**
> *   [NetworkNT JSON Schema Validator](https://github.com/networknt/json-schema-validator) - JSON Schema validation engine
> *   [Cats Validated](https://typelevel.org/cats/datatypes/validated.html) - Functional error accumulation
> *   [Cats Effect](https://typelevel.org/cats-effect/) - Functional concurrency and resource management

