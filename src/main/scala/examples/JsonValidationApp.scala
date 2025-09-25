package examples

import cats.data.Validated.*
import validation.DataValidation
import validation.error.ValidationErrors
import validation.jsonschema.ValidatedSchema

import java.nio.file.{Files, Paths}
import scala.util.Try

/** Command-line JSON validation application that validates JSON strings against multiple schemas.
  *
  * Usage examples (IMPORTANT: quote the schema list if using a semicolon so sbt doesn't treat it as a command separator): sbt "runMain examples.JsonValidationApp
  * --schemas=organisationBase.json,closedRecord.json --json='{"file_size":40}'" sbt "runMain examples.JsonValidationApp --schemas='organisationBase.json;closedRecord.json'
  * --json='{"foi_exemption_code":["23","299"]}'" sbt "runMain examples.JsonValidationApp --schemas=organisationBase.json --json-file=example.json"
  *
  * Parameters: --schemas Comma OR semicolon separated list of schema names (in resources) --json Inline JSON string (quote it!) --json-file Path to file containing JSON
  * (alternative to --json) --debug Turn on argument debug logging
  */
object JsonValidationApp {

  private def cleanQuoted(value: String): String = {
    val trimmed = value.trim
    if (trimmed.length >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))))
      trimmed.substring(1, trimmed.length - 1)
    else trimmed
  }

  private case class CliConfig(
      rawSchemas: Option[String] = None,
      jsonInline: Option[String] = None,
      jsonFile: Option[String] = None,
      debug: Boolean = false
  ) {
    def jsonString: Option[String] =
      jsonInline.map(cleanQuoted).orElse(jsonFile.flatMap(readFile))

    def schemaList: List[String] =
      rawSchemas
        .map(cleanQuoted)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(_.split("[;,]").toList.map(_.trim).filter(_.nonEmpty))
        .getOrElse(Nil)
  }

  private def parseAndValidateJson(raw: String): Either[String, String] = {
    val trimmed = raw.trim
    if (trimmed.isEmpty) Left("JSON payload is empty")
    else {
      io.circe.parser.parse(trimmed) match {
        case Right(json) => Right(json.noSpaces) // canonical compact form passed to validator
        case Left(err) =>
          val hint =
            (if (!trimmed.startsWith("{")) "JSON should start with '{' for an object. " else "") +
              (if (!trimmed.contains('"')) "Looks like keys / string values may be missing double quotes." else "")
          Left(s"Malformed JSON: ${err.getMessage}. ${hint}\nExample: '{\"closure_type\":\"Closed\"}'")
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val config = parseArguments(args)

    if (config.debug) {
      println(s"[debug] Raw args (${args.length}): ${args.mkString(" | ")}")
      println(s"[debug] Parsed schemas: ${config.schemaList}")
      println(s"[debug] Inline JSON defined: ${config.jsonInline.isDefined}")
      println(s"[debug] JSON file defined: ${config.jsonFile.isDefined}")
      println(s"[debug] Effective JSON string length: ${config.jsonString.map(_.length)}")
    }

    (config.schemaList, config.jsonString) match {
      case (Nil, _) =>
        println("❌ No schemas provided. Use --schemas=<list> (comma or semicolon separated).")
        printUsage()
        sys.exit(1)
      case (_, None) =>
        println("❌ No JSON provided. Use --json='{}' or --json-file=path/to/file.json")
        printUsage()
        sys.exit(1)
      case (schemas, Some(jsonRaw)) =>
        parseAndValidateJson(jsonRaw) match {
          case Left(msg) =>
            println(s"❌ Invalid JSON input\n$msg")
            println("-- Hint: wrap the JSON in single quotes and escape internal double quotes when invoking sbt.")
            println(
              """   Example:
                |     sbt "runMain examples.JsonValidationApp --schemas=organisationBase.json --json='{"closure_type":"Closed"}'"
                |     sbt "runMain examples.JsonValidationApp --schemas=organisationBase.json,closedRecord.json --json='{"closure_type":"Closed"}'"
                |""".stripMargin
            )
            sys.exit(2)
          case Right(validJson) =>
            println(s"🔍 Validating JSON against ${schemas.length} schema(s): ${schemas.mkString(", ")}")
            // pretty print for display only
            io.circe.parser.parse(validJson).toOption.foreach { parsed =>
              println(s"📄 JSON to validate:\n${parsed.spaces2}\n")
            }
            validateJsonAgainstSchemas(schemas, validJson)
        }
    }
  }

  private def parseArguments(args: Array[String]): CliConfig = {
    args.foldLeft(CliConfig()) { (cfg, arg) =>
      if (arg.startsWith("--schemas=")) cfg.copy(rawSchemas = Some(arg.drop("--schemas=".length)))
      else if (arg.startsWith("--json-file=")) cfg.copy(jsonFile = Some(arg.drop("--json-file=".length)))
      else if (arg.startsWith("--json=")) cfg.copy(jsonInline = Some(arg.drop("--json=".length)))
      else if (arg == "--debug") cfg.copy(debug = true)
      else cfg
    }
  }

  private def validateJsonAgainstSchemas(schemas: List[String], json: String): Unit = {
    val results = schemas.map { schema =>
      println(s"⚡ Validating against schema: $schema")
      val validationResult: DataValidation = ValidatedSchema.validateJson(schema, json)
      validationResult match {
        case Valid(_) =>
          println(s"✅ Schema $schema: VALID")
          (schema, true, List.empty[ValidationErrors])
        case Invalid(errors) =>
          println(s"❌ Schema $schema: INVALID")
          errors.toList.foreach(printValidationErrors)
          (schema, false, errors.toList)
      }
    }

    // Summary
    println("\n" + "=" * 60)
    println("📊 VALIDATION SUMMARY")
    println("=" * 60)

    val validCount = results.count(_._2)
    val totalCount = results.length

    results.foreach { case (schema, isValid, errors) =>
      val status     = if (isValid) "✅ VALID" else "❌ INVALID"
      val errorCount = if (errors.nonEmpty) s" (${errors.map(_.errors.size).sum: Int} errors)" else ""
      println(f"$schema%-30s $status$errorCount")
    }

    println(s"\nOverall: $validCount/$totalCount schemas passed validation")

    if (validCount == totalCount) {
      println("🎉 All validations passed!")
      sys.exit(0)
    } else {
      println("💥 Some validations failed!")
      sys.exit(1)
    }
  }

  private def printValidationErrors(validationErrors: ValidationErrors): Unit = {
    println(s"   Asset ID: ${validationErrors.assetId}")
    validationErrors.errors.foreach { error =>
      println(s"   🔸 Property: ${error.property}")
      println(s"      Error: ${error.errorKey}")
      println(s"      Message: ${error.message}")
      println(s"      Value: ${if (error.value.nonEmpty) error.value else "null"}")
      println()
    }
  }

  private def readFile(path: String): Option[String] =
    Try(new String(Files.readAllBytes(Paths.get(path)), "UTF-8")).toOption

  private def formatJson(json: String): String =
    Try(io.circe.parser.parse(json).toOption.map(_.spaces2).getOrElse(json)).getOrElse(json)

  private def printUsage(): Unit = {
    println(
      """
        |🚀 JSON Validation Command Line Tool
        |
        |USAGE (quote semicolon separated lists so sbt doesn't split on ';'):
        |  sbt "runMain examples.JsonValidationApp --schemas=organisationBase.json,closedRecord.json --json='{"closure_type":"Closed"}'"
        |  sbt "runMain examples.JsonValidationApp --schemas='organisationBase.json;closedRecord.json' --json='{"foi_exemption_code":["23","299"]}'"
        |  sbt "runMain examples.JsonValidationApp --schemas=organisationBase.json --json-file=example.json"
        |
        |OPTIONS:
        |  --schemas     Comma OR semicolon separated list of schema file names (in resources dir)
        |  --json        Inline JSON string (wrap in single quotes; keys & string values need double quotes)
        |  --json-file   Path to a JSON file (alternative to --json)
        |  --debug       Print parsed argument debug information
        |
        |COMMON PITFALL:
        |  If you see '{closure_type:Closed}', your shell stripped the quotes. Use single quotes around the JSON and escape internal quotes if needed.
        |
        |TIPS:
        |  * Prefer commas to avoid having to quote the schemas list.
        |  * When using semicolons you MUST wrap the whole schema list in quotes.
        |  * You can combine multiple schemas; each is validated independently.
        |  * Provide valid JSON (double-quoted keys). The tool now validates JSON before schema validation.
        |
        |EXAMPLES:
        |  sbt "runMain examples.JsonValidationApp --schemas=organisationBase.json --json='{"foi_exemption_code":["23"]}'"
        |  sbt "runMain examples.JsonValidationApp --schemas=organisationBase.json,closedRecord.json --json='{"closure_type":"Closed"}'"
        |  sbt "runMain examples.JsonValidationApp --schemas='organisationBase.json;closedRecord.json' --json-file=sample.json"
        |
        |EXIT CODES:
        |  0 = All schemas valid
        |  1 = One or more schemas invalid / bad input during validation phase
        |  2 = Malformed JSON input (did not reach schema validation)
        |""".stripMargin
    )
  }
}
