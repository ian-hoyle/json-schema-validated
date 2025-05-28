package validation.custom

import validation.{DataValidationResult, RowData}
import cats.syntax.validated.*

object DebugPrintFirstRow {
  def printFirstRow(data: List[RowData]): DataValidationResult[List[RowData]] = {
    data.headOption match {
      case Some(row) => println(row.json) 
        println(row.data)
      case None => println("No data provided")  
    }
    data.valid
  }
}
