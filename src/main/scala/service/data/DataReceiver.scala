package service.data

import com.typesafe.scalalogging.StrictLogging
import models.db._

abstract class DataReceiver[R] extends StrictLogging {
  def receive(playlist: Playlist): R
  def receive(artist: Artist): R
  def receive(album: Album): R
  def receive(track: Track): R
}