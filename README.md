# json-schema-validated

## Table of Contents
- [Reason for the project](#justification)
- [Overview](#overview)
- [Schema](#schema)
- [Example usage](#example-usage)


## Justification
Needed to support the transfer of data to The National Archives (TNA)
  - The data could be in different formats (CSV, JSON, etc.)
  - The date must be validated so that it can be used in the TNA system
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

`json-schema-validated` is a Scala-based project for validating data using JSON schema language. The data is loaded as key-value pairs, where the keys can vary between domains and then validated against JSON schema.    

The JSON schema validation is performed using [NetowrkNT JSON Schema Validator](http://github.com/networknt/json-schema-validator

Conditional validation is supported by the use of multiple schema

Custom user friendly messages are supported by the use of properties files with simialar names as the schema

## Schema
Two fundamental schema are used in the validation process 
- [The base JSON schema](src/main/resources/organisationBase.json) defining all data and their types.
- [Configuration JSON schema](src/main/resources/config.json) that can be used to [define](src/main/scala/validation/PackageClasses.scala#L15)
  - Alternate keys for mapping between domains
  - Domain specific validations that can't be defined using JSON schema

One or many JSON schema can be used to validate the data with error messages defined for each schema
## Example usage
Example usage of the project is provided in the [examples](src/main/scala/examples) directory. The examples include:

The `csvFileValidation` method in the [CSVFileValidationLambdaHandler](src/main/scala/examples/CSVFileValidationLambdaHandler.scala) class is responsible for validating CSV data against multiple JSON schemas. Here is the method with a brief explanation:

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

The `prepareValidationConfiguration` method in the [ValidationConfig](src/main/scala/config/ValidationConfig.scala) class is responsible for preparing the validation configuration using the provided config file and alternate key. Here is the method with a brief explanation:

```scala
def prepareValidationConfiguration(configFile: String, alternateKey: Option[String]): IO[ValidatorConfiguration] = {
  IO({
    val csvConfigurationReader = for {
      domainKeyToPropertyMapper <- Reader(ValidationConfig.domainKeyToPropertyMapper)
      propertyToDomainKeyMapper <- Reader(ValidationConfig.propertyToDomainKeyMapper)
      valueMapper <- Reader(ValidationConfig.stringValueMapper)
    } yield ValidatorConfiguration(altHeaderToPropertyMapper, propertyToAltHeaderMapper,
      valueMapper)
    csvConfigurationReader.run(ConfigParameters(configFile, alternateKey, "organisationBase.json", decodeConfig(configFile)))
  }
  ) 
}
```

- **domainKeyToPropertyMapper**: Creates a [function](src/main/scala/config/ValidationConfig.scala#L34C3-L42C45) to map alternate keys to properties.
- **propertyToDomainKeyMapper**: Creates a function to map properties to alternate keys.
- **stringValueMapper**: Creates a function to convert string values to their respective types based on the property type. Especially useful for CSV validation
- **ValidatorConfiguration**: Combines the above functions into a `ValidatorConfiguration` object.
 

The method returns an `IO` containing the `ValidatorConfiguration` object.

### generateSchema Task

The `generateSchema` task is an SBT custom task designed to generate JSON schemas based on provided configurations. This task reads a list of JSON files, processes them, and outputs the modified schemas.

The schema generated are copies with the original property names replaced with the alternate keys defined in the configuration file. 

The task is defined in the [GenerateSchema](project/GenerateSchema.scala) object.

```scala

### Usage

To use the `generateSchema` task, follow these steps:

1. **Configure the list of file names**:
   Set the list of JSON file names that you want to process using the `fileNames` setting key.

2. **Run the task**:
   Execute the `sbt generateSchema` task from the console.

