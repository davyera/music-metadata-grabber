package service

import com.typesafe.scalalogging.StrictLogging
import models.ArtistSummary
import service.job.JobOrchestrator

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Server extends App with StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global

  val worker = new JobOrchestrator

  val plistsFuture: Future[Seq[ArtistSummary]] = worker.launchFeaturedPlaylistsJobs()
  plistsFuture.onComplete {
    case Success(summaries) => logArtistSummaries(summaries)
    case Failure(error) => throw error
  }
//  val summaryFuture = worker.launchArtistDataJobsForName("hazel english")
//  summaryFuture.onComplete {
//    case Success(summary) => logArtistSummary(summary)
//    case Failure(error) => throw error
//  }

  private def logArtistSummaries(summaries: Seq[ArtistSummary]): Unit = summaries.foreach(logArtistSummary)

  private def logArtistSummary(summary: ArtistSummary): Unit = {
    logger.info("ARTIST")
    logger.info(summary.artist.toString)
    logger.info("ALBUMS")
    summary.albums.foreach(album => logger.info(album.toString))
    logger.info("TRACKS")
    summary.tracks.foreach(track => logger.info(track.toString))
  }

}
