package validation.jsonschema

import cats.data.Validated
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers.*
import validation.error.ValidationErrors
import validation.{DataValidation, RowData}

class ValidatedSchemaTest extends AnyFunSuite:

  test("test schema validated with valid json") {

    val json =
      """
     {
          "file_size": 40
     }
     """.stripMargin

    val validationResult: DataValidation =
      ValidatedSchema.schemaValidated("organisationBase.json", (x: String) => x)(
        List(createRowData(json))
      )
    val validData = testIsValid(validationResult)
  }

  test("test schema with invalid json no properties error message") {

    val json =
      """
       {
            "file_size": "40"
       }
       """.stripMargin

    val validationResult: DataValidation =
      ValidatedSchema.schemaValidated("organisationBase.json", (x: String) => x)(
        List(createRowData(json))
      )
    val invalidData = testIsInvalid(validationResult)
    invalidData.head.errors.head.message shouldBe "/file_size: string found, integer expected"
  }

  test("test schema with invalid valid json with message in properties file") {

    val json =
      """
        {
             "foi_exemption_code": ["23", "299"]
        }
        """.stripMargin

    val validationResult: DataValidation =
      ValidatedSchema.schemaValidated("organisationBase.json", (x: String) => x)(
        List(createRowData(json))
      )
    val errors = testIsInvalid(validationResult)
    errors.head.errors.head.message shouldBe "Must be a pipe delimited list of valid FOI codes, (eg. 31|33). Please see the guidance for more detail on valid codes"
  }

  def testIsValid(e: DataValidation): List[RowData] = {
    e match {
      case Validated.Valid(data) => data
      case Validated.Invalid(errors) =>
        errors.toList shouldBe empty
        List.empty[RowData]
    }
  }

  def testIsInvalid(e: DataValidation): List[ValidationErrors] = {
    e match {
      case Validated.Valid(data) =>
        data.toList shouldBe empty
        List.empty[ValidationErrors]
      case Validated.Invalid(errors) => errors.toList
    }
  }

  def createRowData(json: String): RowData = {
    RowData(
      Some(1),
      Some("test/test2.txt"),
      Map("File path" -> "test/test2.txt", "description_closed" -> "YES"),
      Some(json)
    )
  }
