package service.job

import com.typesafe.scalalogging.StrictLogging
import models.db.Track
import service.job.spotify.{ArtistAlbumsJob, FeaturedPlaylistsJob, TracksJob}

import scala.concurrent.{ExecutionContext, Future}

class JobOrchestrator(implicit val context: ExecutionContext) extends StrictLogging {

  private implicit val env: JobEnvironment = new JobEnvironment

  def orchestratePlaylistDataJobs(): Future[Unit] = {
    // pull featured playlists
    FeaturedPlaylistsJob().doWork().map { plistTrackMap =>
      plistTrackMap.values.map { tracks: Seq[Track] =>
        val artistIds = tracks.map(_.artists)
      }
    }
    Future.successful()
  }

  def orchestrateArtistTrackJobs(artistId: String): Future[Unit] = {
    ArtistAlbumsJob(artistId, pushData = true).doWork().map { albums =>
      // launch track data jobs for each track
      val tracksIds = albums.flatMap(_.tracks)
    }
    Future.successful()
  }

  def orchestrateTrackDataJobs(trackIds: Seq[String]): Future[Unit] = {
    TracksJob(trackIds, pushData = true).doWork()
    Future.successful()
  }
}