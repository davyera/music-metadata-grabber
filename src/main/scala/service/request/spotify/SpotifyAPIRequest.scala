package service.request.spotify

import io.circe.Decoder
import models._
import models.api.response._
import service.request.APIGetRequest
import sttp.client.UriContext
import sttp.model.Uri

private object SpotifyAPIRequest {}

case class SpotifyCategoriesRequest(private val limit: Int = 25,
                                    private val offset: Int = 0,
                                    private val country: String = "US")
  extends APIGetRequest[SpotifyBrowseCategories] {

  override val uri: Uri = uri"https://api.spotify.com/v1/browse/categories"
    .param("limit", limit.toString)
    .param("offset", offset.toString)
    .param("country", country)

  override implicit val decoder: Decoder[SpotifyBrowseCategories] = spotifyBrowseCategories
}

case class SpotifyCategoryPlaylistsRequest(private val categoryId: String,
                                           private val limit: Int = 25,
                                           private val offset: Int = 0,
                                           private val country: String = "US")
  extends APIGetRequest[SpotifyCategoryPlaylists] {

  override val uri: Uri = uri"https://api.spotify.com/v1/browse/categories/$categoryId/playlists"
    .param("limit", limit.toString)
    .param("offset", offset.toString)
    .param("country", country)

  override implicit val decoder: Decoder[SpotifyCategoryPlaylists] = spotifyCategoryPlaylists

}

case class SpotifyFeaturedPlaylistsRequest(private val limit: Int = 2,
                                           private val offset: Int = 0,
                                           private val country: String = "US")
  extends APIGetRequest[SpotifyFeaturedPlaylists] {

  override val uri: Uri = uri"https://api.spotify.com/v1/browse/featured-playlists"
    .param("limit", limit.toString)
    .param("offset", offset.toString)
    .param("country", country)

  override implicit val decoder: Decoder[SpotifyFeaturedPlaylists] = spotifyFeaturedPlaylists
}

case class SpotifyPlaylistTracksRequest(private val playlistId: String,
                                        private val limit: Int = 10,
                                        private val offset: Int = 0)
  extends APIGetRequest[SpotifyPlaylistTracksPage] {

  override val uri: Uri = uri"https://api.spotify.com/v1/playlists/$playlistId/tracks"
    .param("limit", limit.toString)
    .param("offset", offset.toString)
    .param("fields", requestFieldsString[SpotifyPlaylistTracksPage])

  override implicit val decoder: Decoder[SpotifyPlaylistTracksPage] = spotifyPlaylistTracksPage
}

case class SpotifyArtistRequest(private val artistId: String)
  extends APIGetRequest[SpotifyArtist] {

  override val uri: Uri = uri"https://api.spotify.com/v1/artists/$artistId"

  override implicit val decoder: Decoder[SpotifyArtist] = spotifyArtist
}

case class SpotifyArtistAlbumsRequest(private val artistId: String,
                                      private val limit: Int = 20,
                                      private val offset: Int = 0,
                                      private val includeGroups: String = "album")
  extends APIGetRequest[SpotifyArtistAlbumsPage] {

  override val uri: Uri = uri"https://api.spotify.com/v1/artists/$artistId/albums"
    .param("limit", limit.toString)
    .param("offset", offset.toString)
    .param("include_groups", includeGroups)
    .param("fields", requestFieldsString[SpotifyArtistAlbumsPage])

  override implicit val decoder: Decoder[SpotifyArtistAlbumsPage] = spotifyArtistAlbumsPage
}

case class SpotifyAlbumsRequest(private val albumIds: Seq[String])
  extends APIGetRequest[SpotifyAlbums] {

  override val uri: Uri = uri"https://api.spotify.com/v1/albums"
    .param("ids", albumIds.mkString(","))
    .param("fields", requestFieldsString[SpotifyAlbums])

  override implicit val decoder: Decoder[SpotifyAlbums] = spotifyAlbums
}

case class SpotifyTracksRequest(private val trackIds: Seq[String])
  extends APIGetRequest[SpotifyTracks] {

  override val uri: Uri = uri"https://api.spotify.com/v1/tracks"
    .param("ids", trackIds.mkString(","))
    .param("fields", requestFieldsString[SpotifyTracks])

  override implicit val decoder: Decoder[SpotifyTracks] = spotifyTracks
}

case class SpotifyAudioFeaturesRequest(private val trackIds: Seq[String])
  extends APIGetRequest[SpotifyAudioFeaturesPage] {

  override val uri: Uri = uri"https://api.spotify.com/v1/audio-features"
    .param("ids", trackIds.mkString(","))

  override implicit val decoder: Decoder[SpotifyAudioFeaturesPage] = spotifyAudioFeaturesPage
}
