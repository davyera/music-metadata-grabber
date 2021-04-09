package service.request.genius

import com.typesafe.scalalogging.StrictLogging
import models.api.response.GeniusSong
import org.jsoup.Jsoup

import scala.concurrent.{ExecutionContext, Future}

class GeniusLyricsScraper(implicit val context: ExecutionContext) extends StrictLogging {

  /** Scrapes Genius for lyrics for given songs
   *  @return Seq of tuples: ([[GeniusSong]], Future of its lyrics)
   */
  def scrapeLyrics(songs: Seq[GeniusSong]): Seq[(GeniusSong, Future[String])] = {
    songs.map { song: GeniusSong =>
      song -> Future(scrapeLyrics(song.url))
    }
  }

  protected[genius] def scrapeLyrics(url: String): String = {
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
