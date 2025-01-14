# NewRepositoryName

## Table of Contents
- [Overview](#overview)
- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [AWS Lambda Usage](#aws-lambda-usage)

## Overview

`NewRepositoryName` is a Scala-based library for validating CSV files. It ensures data integrity by performing various checks and validations on CSV data.

## Features

- UTF-8 validation
- Schema validation using JSON schema
- Customizable configuration for alternate key mappings and value transformations

## Installation

To include `NewRepositoryName` in your project, add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "com.example" %% "NewRepositoryName" % "1.0.0"
```