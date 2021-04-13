package service.job.genius

import models.api.response.GeniusArtistSongsPage
import service.job.{GeniusJob, JobFramework}

import scala.concurrent.Future

/** Processes scraping all lyrics for a given artist (given an artist ID that has already been queried) */
case class ArtistLyricsJob(artistId: Int, artistName: String)(implicit jobFramework: JobFramework)
  extends GeniusJob[Unit] {

  private[job] override def work: Future[Unit] = {
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
