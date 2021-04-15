package service.job.genius

import models.api.response.GeniusSong
import models.db.Lyrics
import service.job.{GeniusJob, JobEnvironment}

import scala.concurrent.Future

/** Scrapes Genius for the given song. Requires artist and artist ID info for the data push. */
case class SongLyricsJob(song: GeniusSong, artist: String, artistId: Int)
                        (implicit jobEnvironment: JobEnvironment)
  extends GeniusJob[Unit] {

  override private[job] val jobName = "SONG_LYRICS"

  override private[job] def work: Future[Unit] = {
    geniusScraper.scrapeLyrics(song.url).map { lyrics: String =>
      val lyricsData = Lyrics(lyrics, song.id, song.title, artistId, artist, song.url)
      pushData(lyricsData)
    }
  }
}
