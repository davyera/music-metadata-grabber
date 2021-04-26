package service.data

import com.typesafe.scalalogging.StrictLogging
import models.api.db.{Album, Artist, Playlist, Track}

abstract class DataReceiver extends StrictLogging {
  def receive(playlist: Playlist): Unit
  def receive(artist: Artist): Unit
  def receive(album: Album): Unit
  def receive(track: Track): Unit
}