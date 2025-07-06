package validation.custom

import validation.{DataValidation, Data}
import cats.syntax.validated.*

object DebugPrintFirstRow {
  def printFirstRow(data: List[Data]): DataValidation = {
    data.headOption match {
      case Some(row) =>
        println(row.json)
        println(row.data)
      case None => println("No data provided")
    }
//    data.tail.headOption match {
//      case Some(row) =>
//        println(row.json)
//        println(row.data)
//      case None => println("No data provided")
//    }
    data.valid
  }
}
