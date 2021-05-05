package service.job.genius

import models.LyricsMap
import models.api.resources.genius.GeniusArtistSongsPage
import service.job.{GeniusJob, JobEnvironment}

import scala.concurrent.Future

/** Processes scraping all lyrics for a given artist.
 *  Returns a map of song title to future lyrics result.
 */
case class ArtistLyricsJob(artistName: String)(implicit jobEnvironment: JobEnvironment)
  extends GeniusJob[LyricsMap] {

  override private[job] val jobName = "ARTIST_LYRICS"

  override private[job] val jobIdentifier = s"[$artistName]"

  override private[job] def work: Future[LyricsMap] = {
    queryArtistId().flatMap { artistId =>
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

  override private[job] def recovery: LyricsMap = {
    logWarn(s"Skipping lyrics for artist $artistName.");
    Map()
  }

  private[job] def queryArtistId(): Future[Int] = GeniusArtistIdJob(artistName).doWork()
}
