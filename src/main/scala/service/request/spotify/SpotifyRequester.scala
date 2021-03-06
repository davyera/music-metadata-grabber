package service.request.spotify

import com.typesafe.scalalogging.StrictLogging
import models._
import models.api.resources.spotify._
import service.request.spotify.SpotifySearchType.SpotifySearchType
import service.request.{APIRequester, AuthTokenProvider}

import scala.concurrent.{ExecutionContext, Future}

object SpotifySearchType {
  type SpotifySearchType = String
  val Tracks   = "track"
  val Artists  = "artist"
  val All      = "track,artist"
}

class SpotifyRequester(override val authProvider: AuthTokenProvider)
                      (implicit override val backend: Backend,
                       implicit override val context: ExecutionContext)
  extends APIRequester(authProvider) with StrictLogging {

  /** Perform a Spotify search for artists, tracks or both */
  def search(query: String, searchType: SpotifySearchType, limitPerPage: Int = 25)
  : Future[Seq[Future[SpotifySearch]]] =
    queryPages(limitPerPage, (limit, offset) =>
      searchPage(query, searchType, limit, offset))

  def searchPage(query: String, searchType: SpotifySearchType, limit: Int, offset: Int = 0)
  : Future[SpotifySearch] =
    get(SpotifySearchRequest(query, searchType, limit, offset))

  /** Requests categories for Spotify's browse feature (ie. "Hip Hop", "Top Lists")
   *  @return future-wrapped paginated sequence of futures of categories
   */
  def requestCategories(limitPerPage: Int = 50): Future[Seq[Future[SpotifyBrowseCategories]]] =
    queryPages(limitPerPage, (limit, offset) =>
      requestCategoriesPage(limit, offset))

  private def requestCategoriesPage(limit: Int, offset: Int): Future[SpotifyBrowseCategories] =
    get(SpotifyCategoriesRequest(limit, offset))

  /** Requests Spotify playlists for a specific category ID (ie. "hiphop", "pop")
   *  @return future-wrapped paginated sequence of futures of playlist references
   */
  def requestCategoryPlaylists(categoryId: String, limitPerPage: Int = 25)
  : Future[Seq[Future[SpotifyCategoryPlaylists]]] =
    queryPages(limitPerPage, (limit, offset) =>
      requestCategoryPlaylistsPage(categoryId, limit, offset))

  private def requestCategoryPlaylistsPage(categoryId: String, limit: Int, offset: Int)
  : Future[SpotifyCategoryPlaylists] =
    get(SpotifyCategoryPlaylistsRequest(categoryId, limit, offset))

  /** Iterates through today's featured Spotify playlists. Paginated response.
   *  @return future-wrapped paginated sequence of futures of featured playlist references
   */
  def requestFeaturedPlaylists(limitPerRequest: Int = 25)
  : Future[Seq[Future[SpotifyFeaturedPlaylists]]] =
    queryPages(limitPerRequest, (limit, offset) =>
      requestFeaturedPlaylistsPage(limit, offset))

  private def requestFeaturedPlaylistsPage(limit: Int, offset: Int)
  : Future[SpotifyFeaturedPlaylists] =
    get(SpotifyFeaturedPlaylistsRequest(limit, offset))

  /** Iterates through a Spotify playlist's tracks. Paginated response
   *  @return future-wrapped paginated sequence of futures of playlist tracks
   */
  def requestPlaylistTracks(playlistId: String, limitPerRequest: Int = 25)
  : Future[Seq[Future[SpotifyPlaylistTracksPage]]] =
    queryPages(limitPerRequest, (limit, offset) =>
      requestPlaylistTracksPage(playlistId, limit, offset))

  private def requestPlaylistTracksPage(playlistId: String, limit: Int, offset: Int)
  : Future[SpotifyPlaylistTracksPage] =
    get(SpotifyPlaylistTracksRequest(playlistId, limit, offset))

  /** Requests detailed info on 1 artist
   */
  def requestArtist(artistId: String): Future[SpotifyArtist] =
    get(SpotifyArtistRequest(artistId))

  /** Iterates through an artist's albums. Paginated response
   *  @return future-wrapped paginated sequence of futures of artist albums
   */
  def requestArtistAlbums(artistId: String, limitPerRequest: Int = 20)
  : Future[Seq[Future[SpotifyArtistAlbumsPage]]] =
    queryPages(limitPerRequest, (limit, offset) =>
      requestArtistAlbumsPage(artistId, limit, offset))

  private def requestArtistAlbumsPage(artistId: String, limit: Int, offset: Int)
  : Future[SpotifyArtistAlbumsPage] =
    get(SpotifyArtistAlbumsRequest(artistId, limit, offset))

  /** Requests detailed info on a sequence of albums. Results include references to artist and track references.
   *  @param albumIds MAX NUMBER OF ID's IS 20
   */
  def requestAlbums(albumIds: Seq[String]): Future[SpotifyAlbums] =
    get(SpotifyAlbumsRequest(albumIds))

  /** Requests detailed info on a sequence of tracks. Results include references to artist and album.
   *  @param trackIds MAX NUMBER OF ID's IS 50
   */
  def requestTracks(trackIds: Seq[String]): Future[SpotifyTracks] =
    get(SpotifyTracksRequest(trackIds))

  /** Requests detailed audio features info on a sequence of tracks.
   *  @param trackIds MAX NUMBER OF ID's IS 100
   */
  def requestAudioFeatures(trackIds: Seq[String]): Future[SpotifyAudioFeaturesPage] =
    get(SpotifyAudioFeaturesRequest(trackIds))
}
