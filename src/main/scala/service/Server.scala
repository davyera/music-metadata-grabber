package service

import com.typesafe.scalalogging.StrictLogging
import models.ArtistSummary
import models.api.db.Track
import service.job.JobOrchestrator

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Server extends App with StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global

  val worker = new JobOrchestrator

//  val hiphopPlist = "37i9dQZF1DWT5MrZnPU1zD"
//  testPlaylist(hiphopPlist)
  testFeaturedPlaylists()

  private def testArtist(artistName: String): Unit =
    handleJobFuture(worker.launchArtistDataJobsForName(artistName))(logArtistSummary)

  private def testPlaylist(plist: String): Unit =
    handleJobFuture(worker.launchPlaylistTracksJob(plist))(logTracks)

  private def testFeaturedPlaylists(): Unit =
    handleJobFuture(worker.launchFeaturedPlaylistsJobs())(logArtistSummaries)

  private def handleJobFuture[T](future: Future[T])(fn: T => Unit): Unit =
    future.onComplete {
      case Success(result) => fn(result)
      case Failure(error) => throw error
    }

  private def logArtistSummaries(summaries: Seq[ArtistSummary]): Unit = summaries.foreach(logArtistSummary)

  private def logArtistSummary(summary: ArtistSummary): Unit = {
    logger.info("ARTIST")
    logger.info(summary.artist.toString)
    logger.info("ALBUMS")
    summary.albums.foreach(album => logger.info(album.toString))
    logTracks(summary.tracks)
  }

  private def logTracks(tracks: Seq[Track]): Unit = {
    logger.info("TRACKS")
    tracks.foreach(track => logger.info(track.toString))
  }

}
