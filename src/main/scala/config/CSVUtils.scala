package config

object CSVUtils:
  def getConversionFunction(propertyName: String): String => Any = CSVConfig.config.valueMap.getOrElse(propertyName, (x: String) => x)


