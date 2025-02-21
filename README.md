# json-schmea-validated

## Table of Contents
- [Overview](#overview)
- [Features](#features)

## Overview

`json-schema-validated` is a Scala-based project for validating data using JSON schema language. The data can be loaded as key-value pairs, where the keys can vary between users. The schema has been extended to define alternate keys, allowing the actual validation requirements to be written only once.

## Features

- Schema validation using JSON schema
- Customizable configuration for alternate key mappings and value transformations
- Written in Scala using Cats data types and IO
- Unit tests using ScalaTest

## Example usage

### CSV Validation

The `csvFileValidation` method in the `CSVFileValidationLambdaHandler` class is responsible for validating CSV data against multiple JSON schemas. Here is the method with a brief explanation:

```scala
def csvFileValidation(parameters: Parameters): IO[DataValidationResult[List[RowData]]] = {
  for {
    // Prepare the validation configuration using the provided config file and alternate key
    configuration <- prepareValidationConfiguration(parameters.configFile, parameters.alternateKey)
    
    // Load the CSV data and apply transformations
    data <- IO(
      loadCSVData(parameters.fileToValidate, parameters.idKey)
        andThen mapKeys(configuration.altInToKey)
        andThen addJsonForValidation(configuration.valueMapper)
        andThen validateRequiredSchema(parameters.requiredSchema, configuration.keyToAltIn)
    )
    
    // Validate the data against multiple schemas
    validation <- validateWithMultipleSchema(data, parameters.schema, configuration.keyToAltIn)
  } yield validation
}
```

- **prepareValidationConfiguration**: Prepares the validation configuration using the provided config file and alternate key.
- **loadCSVData**: Loads the CSV data from the specified file and applies a series of transformations:
    - **mapKeys**: Maps the keys using the provided alternate key configuration.
    - **addJsonForValidation**: Converts string values to JSON schema types for validation.
    - **validateRequiredSchema**: Validates the data against the required schema.
- **validateWithMultipleSchema**: Validates the transformed data against multiple JSON schemas.

The method returns an `IO` containing the result of the data validation.

### Configuration Loading

The `prepareValidationConfiguration` method in the `ValidationConfig` class is responsible for preparing the validation configuration using the provided config file and alternate key. Here is the method with a brief explanation:

```scala
def prepareValidationConfiguration(configFile: String, alternateKey: Option[String]): IO[ValidatorConfiguration] = {
  IO({
    val csvConfigurationReader = for {
      altHeaderToPropertyMapper <- Reader(ValidationConfig.alternateKeyToPropertyMapper)
      propertyToAltHeaderMapper <- Reader(ValidationConfig.propertyToInAlternateKeyMapper)
      valueMapper <- Reader(ValidationConfig.stringValueMapper)
    } yield ValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper, valueMapper)
    csvConfigurationReader.run(ConfigParameters(configFile, alternateKey))
  })
}
```

- **alternateKeyToPropertyMapper**: Creates a function to map alternate keys to properties.
- **propertyToInAlternateKeyMapper**: Creates a function to map properties to alternate keys.
- **stringValueMapper**: Creates a function to convert string values to their respective types based on the property type.
- **ValidatorConfiguration**: Combines the above functions into a `ValidatorConfiguration` object.

The method returns an `IO` containing the `ValidatorConfiguration` object.
