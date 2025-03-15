import sbt.*
import sbt.Keys.*
import ujson.*

object GenerateScalaCaseClasses {
  val generateCaseClasses = taskKey[Unit]("Generate domain-specific JSON schemas")
  val caseClasses: Seq[Def.Setting[Task[Unit]]] = Seq(
    generateCaseClasses := {
      val log = streams.value.log
      // Base directory for source resources
      val baseDir = baseDirectory.value
      val resourcesDir = (Compile / resourceDirectory).value
      val jsonFile = resourcesDir / "organisationBase.json"

      // Output directory for generated Scala sources
      val outputDir = baseDir / "src" / "main"/ "scala"/ "schema"
      IO.createDirectory(outputDir)

      // Read JSON file
      val jsonString = IO.read(jsonFile)
      val json = ujson.read(jsonString)

      // Extract properties from the JSON schema
      val properties = json("properties").obj

      // Helper function to convert snake_case to camelCase
      def camelCase(name: String): String = {
        name.split('_').zipWithIndex.map {
          case (s, 0) => s
          case (s, _) => s.capitalize
        }.mkString
      }

      // Map JSON types to Scala types and wrap with Option if the type array includes "null"
      def scalaType(v: ujson.Value): String = {
        v("type") match {
          case Str(tpe) => tpe match {
            case "string" => "String"
            case "integer" => "Int"
            case "boolean" => "Boolean"
            case "array" => "List[String]" // fallback for arrays
            case _ => "String"
          }
          case arr: Arr =>
            val types = arr.value.collect { case Str(s) => s }
            val isNullable = types.contains("null")
            val baseType = types.find(_ != "null").getOrElse("string") match {
              case "string" => "String"
              case "integer" => "Int"
              case "boolean" => "Boolean"
              case "array" => "List[String]"
              case _ => "String"
            }
            if (isNullable) s"Option[$baseType]" else baseType
          case _ => "String"
        }
      }

      // Build fields list
      val fields = properties.map { case (name, propValue) =>
        s"  ${camelCase(name)}: ${scalaType(propValue)}"
      }.mkString(",\n")

      // Build complete case class code
      val caseClassCode =
        s"""package schema
           |
           |case class OrganisationBase(
           |$fields
           |)
           |""".stripMargin

      // Write file
      val outputFile = outputDir / "OrganisationBase.scala"
      IO.write(outputFile, caseClassCode)
      log.info("Generated case class at " + outputFile.getAbsolutePath)
    }
  )
}