package service.job.genius

import models.api.response.GeniusSearchResponse
import service.job.{GeniusJob, JobEnvironment}

import scala.concurrent.Future

/** Performs a Genius search request to extract an artist's ID
 *  @return Genius ID for artist. Throws [[service.job.JobException]] if search yields no results
 */
case class ArtistIdJob(artistName: String)(implicit jobEnvironment: JobEnvironment) extends GeniusJob[Int] {

  private[job] override def work: Future[Int] =
    genius.requestSearchPage(artistName, 1).map { searchResult: GeniusSearchResponse =>
      val hits = searchResult.response.hits
      if (hits.isEmpty)
        throw exception(s"No search results for artist name $artistName")
      else {
        val id = hits.head.result.primary_artist.id
        logInfo(s"Queried ID for artist $artistName: $id")
        id
      }
    }
}
