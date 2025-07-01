package validation.custom

import validation.{DataValidation, RowData}
import cats.syntax.validated.*

object DebugPrintFirstRow {
  def printFirstRow(data: List[RowData]): DataValidation = {
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
