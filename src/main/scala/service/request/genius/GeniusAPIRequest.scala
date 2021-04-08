package service.request.genius

import io.circe.Decoder
import service.APIGetRequest
import sttp.client.UriContext
import sttp.model.Uri

import models._
import models.api.response._

private object GeniusAPIRequest {}

case class GeniusSearchRequest(private val query: String,
                               private val limit: Int = 5,
                               private val page: Int = 1)
  extends APIGetRequest[GeniusSearchResponse] {
  override val uri: Uri = uri"https://api.genius.com/search"
    .param("q", query)
    .param("per_page", limit.toString)
    .param("page", page.toString)

  override implicit val decoder: Decoder[GeniusSearchResponse] = geniusSearchResponse
}

case class GeniusArtistSongsRequest(private val artistId: Int,
                                    private val limit: Int = 20,
                                    private val page: Int = 1)
  extends APIGetRequest[GeniusArtistSongsResponse] {

  override val uri: Uri = uri"https://api.genius.com/artists/$artistId/songs"
    .param("per_page", limit.toString)
    .param("page", page.toString)

  override implicit val decoder: Decoder[GeniusArtistSongsResponse] = geniusArtistSongsResponse
}