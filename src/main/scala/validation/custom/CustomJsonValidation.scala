package validation.custom

import cats.syntax.validated.*
import io.circe.parser.*
import validation.{Data, DataValidation}

object CustomJsonValidation {
  def validateClosureFields(data: List[Data]): DataValidation = {
    data.headOption.flatMap(_.json) match {
      case Some(jsonString) =>
        for {
          parsed <- parse(jsonString).toOption
          fields <- parsed.as[ClosureFields](ClosureFields.closureFieldsDecoder).toOption
        } {
          println("\n=== Closure Fields Decoded ===")
          println(s"Closure Period: ${fields.closurePeriod}")
          println(s"FOI Exemption Code: ${fields.foiExemptionCode}")
          println("=============================\n")
        }
      case None =>
        println("No JSON data found in first item")
    }

    // Always return valid
    data.valid
  }

}
