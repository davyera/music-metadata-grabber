package utils

object MetadataNormalization {

  /** Normalize string -- all lower case & remove special characters */
  def normalizeTitle(title: String): String =
    title.replaceAll("""[^a-zA-Z0-9]""", "").toLowerCase
}
