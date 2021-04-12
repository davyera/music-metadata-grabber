package service.job.genius

import models.api.response.GeniusSong
import models.db.Lyrics
import service.DataReceiver
import service.job.GeniusJob
import service.request.genius.GeniusLyricsScraper

import scala.concurrent.{ExecutionContext, Future}

/** Scrapes Genius for the given song. Requires artist and artist ID info for the data push. */
case class SongLyricsJob(song: GeniusSong, artist: String, artistId: Int)
                        (implicit val genius: GeniusLyricsScraper,
                         implicit override val context: ExecutionContext,
                         implicit override val receiver: DataReceiver)
  extends GeniusJob[Unit] {

  private[job] override def work: Future[Unit] = {
    genius.scrapeLyrics(song.url).map { lyrics: String =>
      val lyricsData = Lyrics(lyrics, song.id, song.title, artistId, artist, song.url)
      sendData(lyricsData)
    }
  }
}
