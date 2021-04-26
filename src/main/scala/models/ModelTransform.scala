package models

import models.api.db.{Album, Artist, Playlist, Track}
import models.api.resources._
import models.api.resources.spotify.{SpotifyAlbum, SpotifyArtist, SpotifyAudioFeatures, SpotifyPlaylistInfo, SpotifyTrack}

/** Plumbing methods for converting various Spotify API JSON response objets to our DB schema */
object ModelTransform {
  def track(sTrk: SpotifyTrack, features: Option[SpotifyAudioFeatures]): Track = {
    val featureMap: Map[String, Float] = features match {
      case Some(f) => f.toMap
      case None => Map()
    }
    Track(sTrk.id, sTrk.name, sTrk.popularity, sTrk.track_number, sTrk.album.id, sTrk.artists.map(_.id), featureMap)
  }

  def playlist(sPlist: SpotifyPlaylistInfo, trackIds: Seq[String]): Playlist =
    Playlist(sPlist.id, sPlist.name, sPlist.description, trackIds)

  def album(sAlb: SpotifyAlbum): Album =
    Album(sAlb.id, sAlb.name, sAlb.popularity, sAlb.artists.map(_.id), sAlb.tracks.items.map(_.id))

  def artist(sArtist: SpotifyArtist, albumIds: Seq[String]): Artist =
    Artist(sArtist.id, sArtist.name, sArtist.genres, sArtist.popularity, albumIds)
}
