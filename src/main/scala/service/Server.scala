package service

import com.typesafe.scalalogging.StrictLogging
import models.ArtistSummary
import models.api.db.Track
import service.job.JobOrchestrator

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Server extends App with StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global

  val worker = new JobOrchestrator

//  val hiphopPlist = "37i9dQZF1DWT5MrZnPU1zD"
//  testPlaylist(hiphopPlist)
//  testFeaturedPlaylists()
  testArtist("hazel english")

  private def testArtist(artistName: String): Unit =
    logArtistSummary(worker.launchArtistDataJobsForName(artistName))

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
