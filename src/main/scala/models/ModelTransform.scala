package models

import models.api.db.{Album, Artist, Playlist, Track}
import models.api.resources.spotify._

/** Plumbing methods for converting various Spotify API JSON response objets to our DB schema */
object ModelTransform {

  def track(sTrk: SpotifyTrack): Track =
    Track(sTrk.id, sTrk.name, sTrk.popularity, sTrk.track_number, sTrk.album.id, sTrk.artists.map(_.id))

  def playlist(sPlist: SpotifyPlaylistInfo, trackIds: Seq[String], category: Option[String]): Playlist =
    Playlist(sPlist.id, sPlist.name, sPlist.description, trackIds, category)

  def album(sAlb: SpotifyAlbum): Album =
    Album(sAlb.id, sAlb.name, sAlb.popularity, sAlb.artists.map(_.id), sAlb.tracks.items.map(_.id))

  def artist(sArtist: SpotifyArtist): Artist =
    Artist(sArtist.id, sArtist.name, sArtist.genres, sArtist.popularity, Nil)
}
