package service.job.spotify

import models.api.response.SpotifySearch
import service.job.{JobEnvironment, JobException, SpotifyJob}
import service.request.spotify.SpotifySearchType

import scala.concurrent.Future

/** Performs a Spotify search request to extract an artist's ID
 *  @return Spotify ID for artist. Throws [[service.job.JobException]] if search yields no results
 */
case class SpotifyArtistIdJob(artistName: String)
                             (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[String] {

  override private[job] val jobName = "ARTIST_ID"

  override private[job] def work: Future[String] = {
    spotify.searchPage(artistName, SpotifySearchType.Artists, 1).map { searchResult: SpotifySearch =>
      searchResult.artists match {
        case Some(artists) =>
          if (artists.items.nonEmpty) {
            val id = artists.items.head.id
            logInfo(s"Queried ID for artist $artistName: $id")
            id
          } else
            throw exception
        case None => throw exception
      }
    }
  }

  private lazy val exception: JobException = exception(s"Could not find Artist ID for $artistName")

}
