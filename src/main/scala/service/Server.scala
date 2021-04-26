package service

import com.typesafe.scalalogging.StrictLogging
import service.job.JobOrchestrator

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Server extends App with StrictLogging {
//object Server extends StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global

  val worker = new JobOrchestrator
//
//  val summaryFuture = worker.launchArtistDataJobsForName("hazel english")
//  summaryFuture.onComplete {
//    case Success(summary) => {
//      logger.info("ARTIST")
//      logger.info(summary.artist.toString)
//      logger.info("ALBUMS")
//      summary.albums.foreach(album => logger.info(album.toString))
//      logger.info("TRACKS")
//      summary.tracks.foreach(track => logger.info(track.toString))
//    }
//    case Failure(error) => throw error
//  }
}
