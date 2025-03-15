import _root_.io.circe.generic.auto.*
import _root_.io.circe.parser.decode
import sbt.Keys.*
import sbt.{Def, *}
import ujson.{Obj, Value}

import java.nio.file.Paths
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}

object CustomTasks {
  // Task and Setting Keys
  val generateSchema = taskKey[Unit]("Generate domain-specific JSON schemas")
  val fileNames = settingKey[Seq[String]]("List of JSON schema file names")

  // Case Classes
  case class JsonConfig(configItems: List[ConfigItem])

  case class ConfigItem(
                         key: String,
                         domainKeys: Option[List[DomainKey]],
                         tdrMetadataDownloadIndex: Option[Int],
                         domainValidations: Option[List[DomainValidation]]
                       )

  case class DomainKey(domain: String, domainKey: String)

  case class DomainValidation(domain: String, domainValidation: String)

  // Constants
  private val AlternateDomain = "TDRMetadataUpload"
  private val ConfigPath = "src/main/resources/config.json"

  val duplications: Seq[Def.Setting[Task[Unit]]] = Seq(
    generateSchema := {
      val log = streams.value.log
      val baseDir = baseDirectory.value

      fileNames.value.foreach { fileName =>
        Try {
          processSchemaFile(baseDir, fileName, log)
        } match {
          case Success(_) =>
          case Failure(e) =>
            log.error(s"Failed to process schema file $fileName: ${e.getMessage}")
        }
      }
    }
  )

  private def processSchemaFile(baseDir: File, fileName: String, log: Logger): Unit = {
    val inputPath = baseDir / "src" / "main" / "resources" / fileName
    val outputPath = baseDir / "src" / "main" / "resources" / s"$AlternateDomain$fileName"

    log.info(s"Processing schema file: $fileName")

    val json = ujson.read(inputPath)
    val transformedJson = replaceKeys(json.obj, propertyKeyToDomainKey)
    val output = ujson.write(transformedJson, indent = 2)

    Using(new java.io.PrintWriter(outputPath)) { writer =>
      writer.write(output)
    }.fold(
      ex => log.error(s"Failed to write output file: ${ex.getMessage}"),
      _ => log.success(s"Successfully generated: ${outputPath.getName}")
    )
  }

  private def replaceKeys(obj: Obj, mapper: String => String): Obj = {
    def transformValue(value: Value): Value = value match {
      case o: Obj => replaceKeys(o, mapper)
      case arr: ujson.Arr => ujson.Arr(arr.value.map(transformValue))
      case other => other
    }

    val newObj = obj.value.map { case (key, value) =>
      mapper(key) -> transformValue(value)
    }

    ujson.Obj.from(newObj)
  }

  private def loadData(path: String): Try[String] = {
    Try {
      val filePath = Paths.get(path).toUri.toURL
      Using.resource(Source.fromURL(filePath))(_.mkString)
    }
  }

  private def decodeConfig(configPath: String): JsonConfig = {
    loadData(configPath).flatMap { data =>
      decode[JsonConfig](data).toTry
    }.getOrElse(JsonConfig(List.empty))
  }

  private def propertyKeyToDomainKey: String => String = {
    val configMap = decodeConfig(ConfigPath).configItems.flatMap { item =>
      item.domainKeys
        .getOrElse(List.empty)
        .find(_.domain == AlternateDomain)
        .map(dk => item.key -> dk.domainKey)
        .orElse(Some(item.key -> item.key))
    }.toMap

    (key: String) => configMap.getOrElse(key, key)
  }
}