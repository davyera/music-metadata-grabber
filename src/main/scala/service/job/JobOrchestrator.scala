package service.job

import com.typesafe.scalalogging.StrictLogging
import models.db.Track
import service.job.genius.{ArtistIdJob, ArtistLyricsJob}
import service.job.spotify.{ArtistAlbumsJob, FeaturedPlaylistsDataJob, TracksJob}

import scala.concurrent.{ExecutionContext, Future}

class JobOrchestrator(implicit val context: ExecutionContext) extends StrictLogging {

  private implicit val env: JobEnvironment = new JobEnvironment

  def orchestratePlaylistDataJobs(): Future[Unit] = {
    // pull featured playlists
    FeaturedPlaylistsDataJob().doWork().map { plistTrackMap =>
      plistTrackMap.values.map { tracks: Seq[Track] =>
        val artistIds = tracks.map(_.artists)
      }
    }
    Future.successful()
  }

  def orchestrateArtistTrackJobs(artistId: String): Future[Unit] = {
    ArtistAlbumsJob(artistId).doWork().map { albums =>
      // launch track data jobs for each track
      val tracksIds = albums.flatMap(_.tracks)
    }
    Future.successful()
  }

  def orchestrateTrackDataJobs(trackIds: Seq[String]): Future[Unit] = {
    TracksJob(trackIds).doWork()
    Future.successful()
  }

  /** Performs a full lyrics-scraping service.job for a given artist
   */
  def orchestrateLyricsJobs(artistName: String): Future[Unit] = {
    // first, we need to query to find the artist ID
    ArtistIdJob(artistName).doWork().map { artistId: Int =>
      // then, perform a Genius request for all of the artist's songs
      ArtistLyricsJob(artistId, artistName).doWork()
    }
  }
}