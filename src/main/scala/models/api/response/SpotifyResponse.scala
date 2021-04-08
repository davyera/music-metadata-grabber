package models.api.response

import models.{AccessToken, PageableWithTotal}

private object SpotifyResponse {}

/** SPOTIFY AUTHORIZATION TOKEN */
case class SpotifyAccessToken(access_token: String, token_type: String, expires_in: Int)
  extends AccessToken {

  override def getAccessToken: String = access_token
  override def expiresIn: Int = expires_in
}

/** SPOTIFY BROWSE CATEGORIES */
case class SpotifyBrowseCategories(categories: SpotifyBrowseCategoriesPage) extends PageableWithTotal {
  def getTotal: Int = categories.total
}
case class SpotifyBrowseCategoriesPage(items: Seq[SpotifyBrowseCategory], total: Int)
case class SpotifyBrowseCategory(id: String, name: String)

/** SPOTIFY CATEGORY PLAYLISTS */
case class SpotifyCategoryPlaylists(playlists: SpotifyCategoryPlaylistsPage) extends PageableWithTotal {
  def getTotal: Int = playlists.total
}
case class SpotifyCategoryPlaylistsPage(items: Seq[SpotifyPlaylistInfo], total: Int)

/** SPOTIFY FEATURED PLAYLISTS */
case class SpotifyFeaturedPlaylists(message: String, playlists: SpotifyPlaylistPage) extends PageableWithTotal {
  def getTotal: Int = playlists.total
}
case class SpotifyPlaylistPage(items: Seq[SpotifyPlaylistInfo], total: Int)
case class SpotifyPlaylistInfo(name: String, id: String, description: String)

/** SPOTIFY PLAYLIST TRACKS */
case class SpotifyPlaylistTracksPage(items: Seq[SpotifyPlaylistTrackRef], total: Int) extends PageableWithTotal {
  def getTotal: Int = total
}
case class SpotifyPlaylistTrackRef(track: SpotifyTrack)

/** SPOTIFY TRACK DETAILS */
case class SpotifyTrack(id: String,
                        name: String,
                        artists: Seq[SpotifyArtistRef],
                        album: SpotifyAlbumRef,
                        track_number: Int,
                        popularity: Int)

/** SPOTIFY ARTIST DETAILS */
case class SpotifyArtist(id: String,
                         name: String,
                         genres: Seq[String],
                         popularity: Int)

/** SPOTIFY ARTIST ALBUMS */
case class SpotifyArtistAlbumsPage(items: Seq[SpotifyAlbumRef], total: Int) extends PageableWithTotal {
  def getTotal: Int = total
}

/** SPOTIFY ALBUM DETAILS */
case class SpotifyAlbums(albums: Seq[SpotifyAlbum])
case class SpotifyAlbum(id: String,
                        name: String,
                        artists: Seq[SpotifyArtistRef],
                        tracks: SpotifyAlbumTracksPage,
                        popularity: Int)
case class SpotifyAlbumTracksPage(items: Seq[SpotifyAlbumTrackRef])
case class SpotifyAlbumTrackRef(name: String, id: String, track_number: Int)

/** SPOTIFY AUDIO FEATURES */
case class SpotifyAudioFeaturesPage(audio_features: Seq[SpotifyAudioFeatures])
case class SpotifyAudioFeatures(id: String,
                                danceability: Float,
                                energy: Float,
                                key: Int,
                                loudness: Float,
                                mode: Int,
                                speechiness: Float,
                                acousticness: Float,
                                instrumentalness: Float,
                                liveness: Float,
                                valence: Float,
                                tempo: Float,
                                duration_ms: Int,
                                time_signature: Int)

/** GENERIC REF OBJECTS */
case class SpotifyArtistRef(name: String, id: String)
case class SpotifyAlbumRef(name: String, id: String)

