package validation.custom

import cats.syntax.validated.*
import validation.{Data, DataValidation}

object CustomJsonValidation {
  def validateClosureFields(data: List[Data]): DataValidation = {
    data.headOption.flatMap(_.json) match {
      case Some(jsonString) =>
        // Use fold to handle both success and failure cases directly
        ClosureFields.fromJson(jsonString).fold(
          error => println(s"Failed to decode JSON: ${error.getMessage}"),
          fields => {
            println(s"Closure Period: ${fields.closurePeriod}")
            println(s"FOI Exemption Code: ${fields.foiExemptionCode}")
          }
        )
      case None =>
        println("No JSON data found in first item")
    }

    // Always return valid
    data.valid
  }
}
