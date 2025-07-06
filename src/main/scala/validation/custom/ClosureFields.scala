package validation.custom

import io.circe.*
import io.circe.parser.*

case class ClosureFields(
    closureType: Option[String] = None,
    closurePeriod: List[Int] = List.empty,
    foiExemptionCode: List[String] = List.empty
)

object ClosureFields {
  implicit val closureFieldsDecoder: Decoder[ClosureFields] = Decoder.instance { cursor =>
    for {
      closureType <- cursor.downField("closure_type").as[Option[String]]
      periods     <- cursor.downField("closure_period").as[Option[List[Int]]].map(_.getOrElse(List.empty))
      codes       <- cursor.downField("foi_exemption_code").as[Option[List[String]]].map(_.getOrElse(List.empty))
    } yield ClosureFields(closureType, periods, codes)
  }

  def fromJson(jsonStr: String): Either[Error, ClosureFields] =
    parse(jsonStr).flatMap(_.as[ClosureFields])
}
