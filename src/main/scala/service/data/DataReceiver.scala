package service.data

import com.typesafe.scalalogging.StrictLogging
import models.db._

abstract class DataReceiver extends StrictLogging {
  def receive(playlist: Playlist): Unit
  def receive(artist: Artist): Unit
  def receive(album: Album): Unit
  def receive(track: Track): Unit
}