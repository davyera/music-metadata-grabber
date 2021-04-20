package service.job.genius

import service.job.{GeniusJob, JobEnvironment}

import scala.concurrent.Future

/** Full job for scraping all lyrics for a given artist name.
 *    1. Launches [[GeniusArtistIdJob]] to find an artist's Genius ID
 *       2. Uses ID to launch lyrics jobs for every song for the artist on Genius
 */
case class ArtistFullLyricsJob(artistName: String)(implicit jobEnvironment: JobEnvironment)
  extends GeniusJob[Map[String, Future[String]]] {

  override private[job] val jobName = "ARTIST_LYRICS_FULL"

  override private[job] def work: Future[Map[String, Future[String]]] = {
    // async ID job
    GeniusArtistIdJob(artistName).doWork().flatMap { id =>
      // async Lyrics job
      ArtistLyricsJob(id).doWork()
    }
  }
}
