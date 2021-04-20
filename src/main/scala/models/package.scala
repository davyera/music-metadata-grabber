import io.circe
import io.circe.Decoder
import io.circe.Error
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import models.api.response._
import sttp.client.{Identity, NothingT, ResponseError}

import scala.concurrent.Future

package object models {
  type Request[R] = sttp.client.RequestT[Identity, Either[ResponseError[Error], R], Nothing]
  type Response[R] = sttp.client.Response[Either[ResponseError[circe.Error], R]]
  type Backend = sttp.client.SttpBackend[Future, Nothing, NothingT]

  implicit val decoder: Configuration = Configuration.default.withDefaults

  implicit val geniusAccessToken: Decoder[GeniusAccessToken] = deriveConfiguredDecoder

  implicit val geniusSearchResponse: Decoder[GeniusSearchResponse] = deriveConfiguredDecoder
  implicit val geniusSearchHits: Decoder[GeniusSearchHits] = deriveConfiguredDecoder
  implicit val geniusSearchHit: Decoder[GeniusSearchHit] = deriveConfiguredDecoder
  implicit val geniusSearchSong: Decoder[GeniusSearchSong] = deriveConfiguredDecoder
  implicit val geniusSearchArtist: Decoder[GeniusSearchArtist] = deriveConfiguredDecoder

  implicit val geniusArtistSongsResponse: Decoder[GeniusArtistSongsPage] = deriveConfiguredDecoder
  implicit val geniusArtistSongs: Decoder[GeniusArtistSongs] = deriveConfiguredDecoder
  implicit val geniusSong: Decoder[GeniusSong] = deriveConfiguredDecoder

  implicit val spotifyAccessToken: Decoder[SpotifyAccessToken] = deriveConfiguredDecoder

  implicit val spotifySearch: Decoder[SpotifySearch] = deriveConfiguredDecoder
  implicit val spotifyArtistsSearchPage: Decoder[SpotifyArtistsSearchPage] = deriveConfiguredDecoder
  implicit val spotifyTracksSearchPage: Decoder[SpotifyTracksSearchPage] = deriveConfiguredDecoder

  implicit val spotifyBrowseCategories: Decoder[SpotifyBrowseCategories] = deriveConfiguredDecoder
  implicit val spotifyBrowseCategoriesPage: Decoder[SpotifyBrowseCategoriesPage] = deriveConfiguredDecoder
  implicit val spotifyBrowseCategory: Decoder[SpotifyBrowseCategory] = deriveConfiguredDecoder

  implicit val spotifyCategoryPlaylists: Decoder[SpotifyCategoryPlaylists] = deriveConfiguredDecoder
  implicit val spotifyCategoryPlaylistsPage: Decoder[SpotifyCategoryPlaylistsPage] = deriveConfiguredDecoder

  implicit val spotifyFeaturedPlaylists: Decoder[SpotifyFeaturedPlaylists] = deriveConfiguredDecoder
  implicit val spotifyPlaylistPage: Decoder[SpotifyPlaylistPage] = deriveConfiguredDecoder

  implicit val spotifyPlaylistInfo: Decoder[SpotifyPlaylistInfo] = deriveConfiguredDecoder
  implicit val spotifyPlaylistTracksPage: Decoder[SpotifyPlaylistTracksPage] = deriveConfiguredDecoder
  implicit val spotifyPlaylistTrackRef: Decoder[SpotifyPlaylistTrackRef] = deriveConfiguredDecoder

  implicit val spotifyTracks: Decoder[SpotifyTracks] = deriveConfiguredDecoder
  implicit val spotifyTrack: Decoder[SpotifyTrack] = deriveConfiguredDecoder

  implicit val spotifyArtistRef: Decoder[SpotifyArtistRef] = deriveConfiguredDecoder
  implicit val spotifyArtist: Decoder[SpotifyArtist] = deriveConfiguredDecoder
  implicit val spotifyArtistAlbumsPage: Decoder[SpotifyArtistAlbumsPage] = deriveConfiguredDecoder
  implicit val spotifyAlbumRef: Decoder[SpotifyAlbumRef] = deriveConfiguredDecoder

  implicit val spotifyAlbums: Decoder[SpotifyAlbums] = deriveConfiguredDecoder
  implicit val spotifyAlbum: Decoder[SpotifyAlbum] = deriveConfiguredDecoder
  implicit val spotifyAlbumTracksPage: Decoder[SpotifyAlbumTracksPage] = deriveConfiguredDecoder
  implicit val spotifyAlbumTrackRef: Decoder[SpotifyAlbumTrackRef] = deriveConfiguredDecoder

  implicit val spotifyAudioFeaturesPage: Decoder[SpotifyAudioFeaturesPage] = deriveConfiguredDecoder
  implicit val spotifyAudioFeatures: Decoder[SpotifyAudioFeatures] = deriveConfiguredDecoder

}
