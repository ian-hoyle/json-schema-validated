package validation.jsonschema

import cats.*
import cats.data.Validated._
import cats.data.Validated.Valid
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import org.scalatest.funsuite.AnyFunSuite
import validation.jsonschema.ValidatedSchema.{CSVValidationResult, schemaValidator}

class ValidatedSchemaTest extends AnyFunSuite:

  test("test validated chain") {

    val firstValidated: String => IO[CSVValidationResult[String]] = schemaValidator("ab")
    val secondValidated: String => IO[CSVValidationResult[String]] = schemaValidator("333")
    val thirdValidated: String => IO[CSVValidationResult[String]] = schemaValidator("333")

    val myCreatedValidated: List[String => IO[CSVValidationResult[String]]] = List(
      firstValidated,
      secondValidated
    )

    def validateInput(input: String, validationsA: List[String => IO[CSVValidationResult[String]]]): IO[CSVValidationResult[List[String]]] = {
      validationsA.map(f => f(input)).parSequence.map(_.sequence)
    }

    val invalid = validateInput("", myCreatedValidated).unsafeRunSync()
    invalid match {
      case Valid(validResults) =>
        fail("Should not be valid")
      case Invalid(errors) =>
        assert(errors.toList.size == 8)
    }

    val result = validateInput("ian", myCreatedValidated).unsafeRunSync()
    result match {
      case Valid(validResults) =>
        assert(validResults == List("an", "an"))
      case Invalid(errors) =>
        fail(s"Expected validation to succeed, but got errors: ${errors}")
    }
    assert(result.toList.head.size == 2)
    //println(validateInput("", myCreatedValidated).unsafeRunSync().foreach(println))

  }




