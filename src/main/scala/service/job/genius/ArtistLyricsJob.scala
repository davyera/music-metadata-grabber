package service.job.genius

import models.api.response.GeniusArtistSongsPage
import service.job.{GeniusJob, JobEnvironment}

import scala.concurrent.Future

/** Processes scraping all lyrics for a given artist (given an artist ID that has already been queried) */
case class ArtistLyricsJob(artistId: Int, artistName: String)(implicit jobEnvironment: JobEnvironment)
  extends GeniusJob[Unit] {

  override private[job] val jobName = "ARTIST_LYRICS"

  override private[job] def work: Future[Unit] = {
    genius.requestArtistSongs(artistId).map { songsResponsePages: Seq[Future[GeniusArtistSongsPage]] =>
      workOnPages(songsResponsePages) { page: GeniusArtistSongsPage =>
        logInfo(s"Received page of ${page.response.songs.size} songs for artist $artistName ($artistId)")
        page.response.songs.map { song =>
          // launch a job for scraping the lyrics for each song
          SongLyricsJob(song, artistName, artistId).doWork()
        }
      }
    }
  }
}
