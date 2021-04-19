package service.job

import com.typesafe.scalalogging.StrictLogging
import models.db.{Album, Track}
import service.job.genius.ArtistFullLyricsJob
import service.job.spotify.{ArtistJob, FeaturedPlaylistsJob, TracksJob}

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

  def orchestrateArtistTracks(artistName: String, artistId: String): Future[Unit] = {
    // push full Artist & Album data first
    val tracks = ArtistJob(artistId, pushData = true).doWork().flatMap { case (_, albums: Seq[Album]) =>

      // launch TracksJob to pull Spotify tracks data once we have all the track ID's for the artist's albums
      val trackIds = albums.flatMap(_.tracks)
      // note: we are not pushing data yet as we will want to combine with lyrics
      TracksJob(trackIds, pushData = false).doWork()
    }

    val lyricsMap = ArtistFullLyricsJob(artistName).doWork()

    TrackLyricsCombinationJob(tracks, lyricsMap, pushData = true).doWork()
    Future.successful()
  }

  def orchestrateTrackDataJobs(trackIds: Seq[String]): Future[Unit] = {
    TracksJob(trackIds, pushData = true).doWork()
    Future.successful()
  }
}