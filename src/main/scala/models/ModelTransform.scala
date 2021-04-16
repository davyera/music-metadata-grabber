package models

import models.db._
import models.api.response._

/**
 * Plumbing methods for converting various Spotify API JSON response objets to our DB schema
 */
object ModelTransform {
  def track(sTrk: SpotifyTrack, features: Option[SpotifyAudioFeatures]): Track = {
    val featureMap: Map[String, Float] = features match {
      case Some(f) => f.toMap
      case None => Map()
    }
    Track(sTrk.id, sTrk.name, sTrk.popularity, sTrk.track_number, sTrk.album.id, sTrk.artists.map(_.id), featureMap)
  }

  def playlist(sPlist: SpotifyPlaylistInfo, trackIds: Seq[String]): Playlist = {
    Playlist(sPlist.id, sPlist.name, sPlist.description, trackIds)
  }

  def album(sAlb: SpotifyAlbum): Album = {
    Album(sAlb.id, sAlb.name, sAlb.popularity, sAlb.artists.map(_.id), sAlb.tracks.items.map(_.id))
  }
}
