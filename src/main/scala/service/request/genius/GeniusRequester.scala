package service.request.genius

import com.typesafe.scalalogging.StrictLogging
import models.Backend
import models.api.response.GeniusArtistSongsResponse
import service.{APIRequester, AuthTokenProvider}

import scala.concurrent.{ExecutionContext, Future}

class GeniusRequester(implicit override val authProvider: AuthTokenProvider,
                      implicit override val backend: Backend,
                      implicit override val context: ExecutionContext)
  extends APIRequester with StrictLogging {

  /** Requests tracks for a Genius artist
   *  @return future-wrapped paginated sequence of futures of categories
   */
  def requestArtistSongs(artistId: Int, limitPerPage: Int = 20): Future[Seq[Future[GeniusArtistSongsResponse]]] =
    queryPagesSequential(limitPerPage, (limit, page) =>
      requestArtistSongsPage(artistId, limit, page))

  private def requestArtistSongsPage(artistId: Int, limitPerPage: Int, page: Int): Future[GeniusArtistSongsResponse] =
    get(GeniusArtistSongsRequest(artistId, limitPerPage, page))

}
