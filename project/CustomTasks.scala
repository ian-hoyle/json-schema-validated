import _root_.io.circe.generic.auto.*
import _root_.io.circe.parser.decode
import sbt.Keys.*
import sbt.{Def, *}
import ujson.Obj

import java.nio.file.Paths
import scala.io.Source
import scala.util.{Failure, Success, Try, Using}

object CustomTasks {

  val generateSchema = taskKey[Unit]("My custom task")
  val fileNames = settingKey[Seq[String]]("List of file names")

  case class JsonConfig(configItems: List[ConfigItem])
  case class ConfigItem(key: String, domainKeys: Option[List[DomainKey]], tdrMetadataDownloadIndex: Option[Int], domainValidations: Option[List[DomainValidation]])
  case class DomainKey(domain: String, domainKey: String)
  case class DomainValidation(domain: String, domainValidation: String)

  def loadData(mySchema: String): Try[String] = {
    Using(Source.fromURL(Paths.get(mySchema).toUri.toURL))(_.mkString)
  }

  val duplications: Seq[Def.Setting[Task[Unit]]] = Seq(
    generateSchema := {
      val baseDir = baseDirectory.value
      val files = fileNames.value

      files.foreach { fileName =>
        val baseFile: File = baseDir / "src" / "main" / "resources" / fileName
        val alternateDomain = "TDRMetadataUpload" // Replace with your actual alternate domain
        val outputFile = baseDir / "src" / "main" / "resources" / s"$alternateDomain$fileName"

        val json = ujson.read(baseFile)
        val out: String = ujson.write(replaceKeys(json.obj, propertyKeyToDomainKey), indent = 2)
        import java.io.*
        val pw = new PrintWriter(outputFile)
        pw.write(out)
        pw.close()
      }
    }
  )

  def replaceKeys(obj: Obj, mapper: String => String): Obj = {
    val newObj = obj.value.foldLeft(collection.mutable.LinkedHashMap[String, ujson.Value]()) {
      case (acc, (key, value)) =>
        val newKey = mapper(key)
        acc += (newKey -> (value match {
          case o: Obj => replaceKeys(o, mapper)
          case arr: ujson.Arr => ujson.Arr(arr.value.map {
            case o: Obj => replaceKeys(o, mapper)
            case other => other
          })
          case other => other
        }))
    }
    ujson.Obj.from(newObj)
  }

  def decodeConfig(csConfig: String): JsonConfig = {
    val configFile: Try[String] = loadData(csConfig)
    val configData = configFile match {
      case Success(data) => decode[JsonConfig](data).getOrElse(JsonConfig(List.empty[ConfigItem]))
      case Failure(exception) => JsonConfig(configItems = List.empty[ConfigItem])
    }
    configData
  }

  def propertyKeyToDomainKey: String => String = {
    val configMap: Map[String, String] = decodeConfig("src/main/resources/config.json").configItems.foldLeft(Map[String, String]())((acc, item) => {
      item.domainKeys match {
        case Some(domainKeys) => domainKeys.find(x => x.domain == "TDRMetadataUpload") match {
          case Some(domainKey) => acc + (item.key -> domainKey.domainKey)
          case None => acc + (item.key -> item.key)
        }
        case None => acc + (item.key -> item.key)
      }
    })
    (x: String) => configMap.getOrElse(x, x)
  }
}