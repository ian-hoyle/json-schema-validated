import sbt.*
import Keys.*
import scala.util.Using
import scala.io.Source

/** Auto plugin to generate Scala case classes from JSON Schema files located in src/main/resources/schema.
  * Each schema file with a top-level "properties" object becomes one case class whose name is derived
  * from its filename (PascalCase). Nested object properties and array item object definitions become
  * additional nested case classes in the same generated file.
  */
object SchemaCaseClassGeneratorPlugin extends AutoPlugin {
  object autoImport {
    val schemaDirectory          = settingKey[File]("Directory containing JSON Schema files")
    val generateSchemaCaseClasses = taskKey[Seq[File]]("Generate Scala case classes from JSON Schemas")
    val schemaCaseClassPackage    = settingKey[String]("Package name for generated case classes")
  }
  import autoImport.*

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Setting[_]] = Seq(
    schemaDirectory := (Compile / resourceDirectory).value / "schema",
    schemaCaseClassPackage := "schema.generated",
    Compile / sourceGenerators += generateSchemaCaseClasses.taskValue,
    generateSchemaCaseClasses := {
      val log       = streams.value.log
      val dir       = schemaDirectory.value
      val outDir    = (Compile / sourceManaged).value / "schema"
      val pkg       = schemaCaseClassPackage.value
      IO.createDirectory(outDir)

      if (!dir.exists()) {
        log.warn(s"[SchemaCaseClassGenerator] Directory does not exist: $dir")
        Seq.empty
      } else {
        import ujson.*

        def toCamel(name: String): String = {
          val parts = name.split("[_-]").filter(_.nonEmpty)
          (parts.headOption.map(_.toLowerCase).getOrElse("") + parts.drop(1).map(_.capitalize).mkString)
        }
        def toPascal(name: String): String = name.split("[_-]").filter(_.nonEmpty).map(_.capitalize).mkString
        def sanitizeForClass(raw: String): String = {
          val noExt = raw.replaceAll("\\.json$", "")
          val parts = noExt.split("[^A-Za-z0-9]+").filter(_.nonEmpty)
          val candidate = parts.map(_.capitalize).mkString
          if (candidate.isEmpty) "Schema" else if (candidate.head.isDigit) "_${candidate}" else candidate
        }

        def baseType(t: String): String = t match {
          case "string"  => "String"
          case "integer" => "Int"
          case "number"  => "Double"
          case "boolean" => "Boolean"
          case "array"   => "List[String]" // fallback, refined below when items inspected
          case _          => "String"
        }

        def schemaScalaType(prop: Value, fieldName: String, nestedAcc: collection.mutable.ListBuffer[String]): String = {
          prop.obj.get("type") match {
            case Some(Str(tpe)) if tpe == "object" =>
              val nestedName  = toPascal(fieldName)
              val nestedProps = prop.obj.get("properties").collect { case o: Obj => o }.getOrElse(Obj())
              val nestedFields = nestedProps.value.map { case (n, v) =>
                val t = schemaScalaType(v, n, nestedAcc)
                s"  ${toCamel(n)}: $t"
              }.mkString(",\n")
              nestedAcc += s"case class $nestedName(\n$nestedFields\n)"
              nestedName
            case Some(Str("array")) =>
              val itemsType: String = prop.obj.get("items") match {
                case Some(itemsObj: Obj) =>
                  if (itemsObj.value.get("properties").exists(_.isInstanceOf[Obj])) {
                    val nestedName  = toPascal(fieldName + "Item")
                    val nestedProps = itemsObj.value("properties").asInstanceOf[Obj]
                    val nestedFields = nestedProps.value.map { case (n, v) =>
                      val t = schemaScalaType(v, n, nestedAcc)
                      s"  ${toCamel(n)}: $t"
                    }.mkString(",\n")
                    nestedAcc += s"case class $nestedName(\n$nestedFields\n)"
                    nestedName
                  } else itemsObj.value.get("type") match {
                    case Some(Str(simple)) => baseType(simple)
                    case _                  => "String"
                  }
                case Some(Str(simple)) => baseType(simple)
                case _                 => "String"
              }
              s"List[$itemsType]"
            case Some(Str(simple)) => baseType(simple)
            case Some(arr: Arr) =>
              val types     = arr.value.collect { case Str(s) => s }
              val nullable  = types.contains("null")
              val main      = types.find(_ != "null").getOrElse("string")
              val mapped    = baseType(main)
              if (nullable) s"Option[$mapped]" else mapped
            case _ => "String"
          }
        }

        val schemaFiles = (dir ** "*.json").get
        val generatedFiles = schemaFiles.flatMap { f =>
          try {
            val json     = ujson.read(f)
            val propsOpt = json.obj.get("properties").collect { case o: Obj => o }
            propsOpt match {
              case Some(props) =>
                val rawName   = f.getName
                val className = sanitizeForClass(rawName)
                val nestedBuf = collection.mutable.ListBuffer.empty[String]
                val fields = props.value.map { case (name, value) =>
                  val t = schemaScalaType(value, name, nestedBuf)
                  s"  ${toCamel(name)}: $t"
                }.mkString(",\n")
                val nestedClasses = if (nestedBuf.nonEmpty) nestedBuf.mkString("\n\n") + "\n\n" else ""
                val code =
                  s"""|package %s
                     |// AUTO-GENERATED FROM %s -- DO NOT EDIT
                     |%scase class %s (
                     |%s
                     |)
                     |""".stripMargin.format(pkg, f.getName, nestedClasses, className, fields)
                val outFile = outDir / s"$className.scala"
                IO.write(outFile, code)
                log.info(s"[SchemaCaseClassGenerator] Generated: ${outFile.getName}")
                List(outFile)
              case None =>
                log.warn(s"[SchemaCaseClassGenerator] No top-level properties in ${f.getName}; skipped")
                Nil
            }
          } catch {
            case e: Throwable =>
              log.error(s"[SchemaCaseClassGenerator] Failed for ${f.getName}: ${e.getMessage}")
              Nil
          }
        }
        generatedFiles
      }
    }
  )
}
