package service.request.genius

import com.typesafe.scalalogging.StrictLogging
import org.jsoup.Jsoup

class GeniusLyricsScraper extends StrictLogging {

  def scrapeLyrics(url: String): String = {
    val fullDocument = Jsoup.connect(url).get()
    val rawLyrics = fullDocument.select(".lyrics").text()
    val cleanedLyrics = cleanLyrics(rawLyrics)
    cleanedLyrics
  }

  private val markerRegex = """[\(\[].*?[\)\]]"""
  protected[genius] def cleanLyrics(rawLyrics: String): String =
    rawLyrics
      .replaceAll(markerRegex, "") // replace song markers like [Verse], [Chorus 2], etc
      .replaceAll(" +", " ") // remove double spaces that may have been introduced
      .trim

}
