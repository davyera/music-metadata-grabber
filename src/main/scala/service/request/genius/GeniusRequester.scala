package service.request.genius

import com.typesafe.scalalogging.StrictLogging
import models.Backend
import models.api.resources.genius.{GeniusArtistSongsPage, GeniusSearchResponse}
import service.request.{APIRequester, AuthTokenProvider}

import scala.concurrent.{ExecutionContext, Future}

class GeniusRequester(override val authProvider: AuthTokenProvider)
                     (implicit override val backend: Backend,
                      implicit override val context: ExecutionContext)
  extends APIRequester(authProvider) with StrictLogging {

  /** Requests specific page of Genius search results for the given query.
   */
  def requestSearchPage(query: String, limitPerPage: Int = 5, page: Int = 1): Future[GeniusSearchResponse] =
    get(GeniusSearchRequest(query, limitPerPage, page))

  /** Requests tracks for a Genius artist
   *  @return future-wrapped paginated sequence of futures of categories
   */
  def requestArtistSongs(artistId: Int, limitPerPage: Int = 20): Future[Seq[Future[GeniusArtistSongsPage]]] =
    queryPagesSequential(limitPerPage, (limit, page) =>
      requestArtistSongsPage(artistId, limit, page))

  private def requestArtistSongsPage(artistId: Int, limitPerPage: Int, page: Int): Future[GeniusArtistSongsPage] =
    get(GeniusArtistSongsRequest(artistId, limitPerPage, page))

}
