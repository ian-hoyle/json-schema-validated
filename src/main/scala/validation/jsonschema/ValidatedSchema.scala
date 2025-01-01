package validation.jsonschema

import cats.*
import cats.data.Validated.*
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.effect.IO
import cats.implicits.*
import cats.syntax.all.catsSyntaxValidatedId
import csv.RowData
import error.ValidationErrors

import scala.util.Random

object ValidatedSchema:

  type CSVValidationResult[A] = ValidatedNel[ValidationErrors, A]

  def schemaValidator(schemaFile: String)(valueToValidate: String): IO[CSVValidationResult[String]] = {
    val d = schemaFile
    validateName(valueToValidate)
  }

  def requiredSchemaValidated(schemaFile:Option[String])(data:List[RowData]): CSVValidationResult[List[RowData]] = {
    if (schemaFile.nonEmpty & schemaFile.get.nonEmpty)
      data.valid
    else
    NonEmptyList.fromList(List[ValidationErrors](ValidationErrors("b", List(error.Error("d", "e", "f", "f")).toSet))).get.invalid
  }

  // Step 1: Define validation functions
  private def validateName(name: String): IO[CSVValidationResult[String]] =
    val f = "ah"
    IO {
      val g = (Random().nextInt() % 10000).abs.toString
      if (name.nonEmpty){
        println(f)
        name.substring(1).valid
      }
      else NonEmptyList.fromList(List[ValidationErrors](ValidationErrors("a", List(error.Error(f, f, f, f)).toSet),
        ValidationErrors("a", List(error.Error(f, f, f, f)).toSet),
        ValidationErrors("a", List(error.Error(g, g, g, g)).toSet),
        ValidationErrors("b", List(error.Error(f, f, f, f)).toSet))).get.invalid
    }

  def validateWithSchema(input: String, validationsA: List[String => IO[CSVValidationResult[String]]]): IO[CSVValidationResult[List[String]]] = {
    validationsA.map(f => f(input)).parSequence.map(_.sequence)
  }

