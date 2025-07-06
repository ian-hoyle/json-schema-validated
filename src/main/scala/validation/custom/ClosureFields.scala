package validation.custom

import io.circe.*
import io.circe.parser.*

case class ClosureFields(
    closurePeriod: List[Int] = List.empty,
    foiExemptionCode: List[String] = List.empty
)

object ClosureFields {
  implicit val closureFieldsDecoder: Decoder[ClosureFields] = Decoder.instance { cursor =>
    for {
      periods <- cursor.downField("closure_period").as[Option[List[Int]]].map(_.getOrElse(List.empty))
      codes   <- cursor.downField("foi_exemption_code").as[Option[List[String]]].map(_.getOrElse(List.empty))
    } yield ClosureFields(periods, codes)
  }

  def fromJson(jsonStr: String): Either[Error, ClosureFields] =
    parse(jsonStr).flatMap(_.as[ClosureFields])
}
