package schema

case class OrganisationBase(
  clientSideChecksum: String,
  fileSize: Int,
  UUID: String,
  filePath: String,
  dateLastModified: String,
  description: Option[String],
  endDate: Option[String],
  fileNameTranslation: Option[String],
  fileName: String,
  language: List[String],
  descriptionAlternate: Option[String],
  descriptionClosed: Boolean,
  foiExemptionAsserted: Option[String],
  foiExemptionCode: Option[List[String]],
  closureType: String,
  closurePeriod: Option[Int],
  closureStartDate: Option[String],
  titleClosed: Boolean,
  titleAlternate: Option[String],
  formerReferenceDepartment: Option[String]
)
