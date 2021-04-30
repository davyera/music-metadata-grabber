package service.job.genius

import models.api.resources.genius.GeniusSearchResponse
import service.job.{GeniusJob, JobEnvironment}
import utils.MetadataNormalization.normalizeTitle

import scala.concurrent.Future

/** Performs a Genius search request to extract an artist's ID
 *  @return Genius ID for artist. Throws [[service.job.JobException]] if search yields no results
 */
case class GeniusArtistIdJob(artistName: String)(implicit jobEnvironment: JobEnvironment) extends GeniusJob[Int] {

  override private[job] val jobName = "ARTIST_ID"

  override private[job] def work: Future[Int] =
    genius.requestSearchPage(artistName, 1).map { searchResult: GeniusSearchResponse =>
      val hits = searchResult.response.hits
      if (hits.isEmpty)
        throw exception(s"No search results for artist name $artistName")
      else {
        val artist = hits.head.result.primary_artist
        if (!normalizeTitle(artist.name).equals(normalizeTitle(artistName))) {
          // we got a search result for a different artist -- no lyrics can be found
          throw exception(s"No valid ID for artist $artistName")
        }
        val id = artist.id
        logInfo(s"Queried ID for artist $artistName: $id")
        id
      }
    }

  override private[job] val canRecover: Boolean = false
  override private[job] def recovery: Int = -1
}
