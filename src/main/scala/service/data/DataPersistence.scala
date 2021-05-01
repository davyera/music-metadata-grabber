package service.data

import com.typesafe.scalalogging.StrictLogging
import models.api.db.{Album, Artist, Playlist, Track}

import scala.concurrent.Future

abstract class DataPersistence extends StrictLogging {
  def persist(playlist: Playlist): Unit
  def persist(artist: Artist): Unit
  def persist(album: Album): Unit
  def persist(track: Track): Unit

  def getAlbumsForArtist(artistId: String): Future[Seq[Album]]

  def deleteData(): Future[Boolean]
}