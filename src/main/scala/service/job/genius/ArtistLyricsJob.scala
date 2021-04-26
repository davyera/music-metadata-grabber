package service.job.genius

import models.api.resources.genius.GeniusArtistSongsPage
import service.job.{GeniusJob, JobEnvironment}

import scala.concurrent.Future

/** Processes scraping all lyrics for a given artist (given an artist ID that has already been queried)
 *  Returns a map of song title to future lyrics result.
 */
case class ArtistLyricsJob(artistId: Int)(implicit jobEnvironment: JobEnvironment)
  extends GeniusJob[Map[String, Future[String]]] {

  override private[job] val jobName = "ARTIST_LYRICS"

  override private[job] def work: Future[Map[String, Future[String]]] = {
    genius.requestArtistSongs(artistId).map { songsResponsePages: Seq[Future[GeniusArtistSongsPage]] =>
      val result = workOnPages(songsResponsePages) { page: GeniusArtistSongsPage =>
        page.response.songs.map { song =>
          // launch a job for scraping the lyrics for each song
          song.title -> SongLyricsJob(song.url).doWork()
        }
      }
      awaitPagedResults(result).toMap
    }
  }
}
