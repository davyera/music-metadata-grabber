package service.job

import com.typesafe.scalalogging.StrictLogging
import service.job.genius.{ArtistIdJob, ArtistLyricsJob}

import scala.concurrent.{ExecutionContext, Future}

class JobOrchestrator(implicit val context: ExecutionContext) extends StrictLogging {

  private implicit val framework: JobFramework = new JobFramework

  def orchestratePlaylistDataJobs(): Future[Unit] = {
    // pull featured playlists

    // query each playlist for its tracks

    // for each track, query the artist

    // for each artist, query their albums

    // for each album, query its tracks

    // for each track, query its features
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