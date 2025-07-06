package validation.decoder

import io.circe._
import io.circe.parser._
import io.circe.generic.semiauto._
import validation.Data

object CustomCirceDecoders {

  // Custom decoder for handling array fields in JSON data
  implicit val jsonDataDecoder: Decoder[Map[String, Any]] = new Decoder[Map[String, Any]] {
    def apply(c: HCursor): Decoder.Result[Map[String, Any]] = {
      // Start with empty map and traverse through JSON fields
      c.keys.getOrElse(Iterable.empty).foldLeft(Right(Map.empty[String, Any]): Decoder.Result[Map[String, Any]]) { case (acc, key) =>
        acc.flatMap { map =>
          // Special handling for closure_period and foi_exemption_code as arrays
          if (key == "closure_period" || key == "foi_exemption_code") {
            c.downField(key).as[Json].flatMap { jsonValue =>
              if (jsonValue.isArray) {
                jsonValue.asArray match {
                  case Some(arr) =>
                    // Convert JSON array to Scala List
                    Right(map + (key -> arr.map(_.toString.trim.stripPrefix("\"").stripSuffix("\"")).toList))
                  case None =>
                    // Fallback if it's marked as array but isn't actually one
                    Right(map + (key -> List(jsonValue.toString.trim.stripPrefix("\"").stripSuffix("\""))))
                }
              } else if (jsonValue.isString) {
                // Handle potential string value that should be an array
                val strValue = jsonValue.asString.getOrElse("")
                if (strValue.contains(",")) {
                  // Split comma-separated string into list
                  Right(map + (key -> strValue.split(",").map(_.trim).toList))
                } else if (!strValue.isEmpty) {
                  // Single value string as a one-item list
                  Right(map + (key -> List(strValue)))
                } else {
                  // Empty string becomes empty list
                  Right(map + (key -> List.empty[String]))
                }
              } else if (jsonValue.isNull) {
                // Handle null values as empty list
                Right(map + (key -> List.empty[String]))
              } else {
                // For any other type, convert to string and wrap in a list
                Right(map + (key -> List(jsonValue.toString)))
              }
            }
          } else {
            // Standard handling for other fields
            c.downField(key).as[Json].map { jsonValue =>
              // Convert JSON values to appropriate Scala types
              val value = jsonValue match {
                case j if j.isNull    => null
                case j if j.isBoolean => j.asBoolean.get
                case j if j.isNumber  => j.asNumber.get.toDouble
                case j if j.isString  => j.asString.get
                case j if j.isArray   => j.asArray.get.map(processJsonValue).toList
                case j if j.isObject  => j.asObject.get.toMap.map { case (k, v) => k -> processJsonValue(v) }
                case _                => jsonValue.toString
              }
              map + (key -> value)
            }
          }
        }
      }
    }

    // Helper function to process individual JSON values
    private def processJsonValue(json: Json): Any = json match {
      case j if j.isNull    => null
      case j if j.isBoolean => j.asBoolean.get
      case j if j.isNumber  => j.asNumber.get.toDouble
      case j if j.isString  => j.asString.get
      case j if j.isArray   => j.asArray.get.map(processJsonValue).toList
      case j if j.isObject  => j.asObject.get.toMap.map { case (k, v) => k -> processJsonValue(v) }
      case _                => json.toString
    }
  }

  // Function to parse JSON string to Data object
  def parseJsonToData(json: String, row_number: Option[Int] = None, assetId: Option[String] = None): Either[Error, Data] = {
    decode[Map[String, Any]](json)(jsonDataDecoder).map { dataMap =>
      Data(
        row_number = row_number,
        assetId = assetId,
        data = dataMap,
        json = Some(json)
      )
    }
  }
}
