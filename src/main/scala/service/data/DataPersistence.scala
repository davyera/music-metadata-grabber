package service.data

import com.typesafe.scalalogging.StrictLogging
import models.OrchestrationSummary
import models.api.db.{Album, Artist, Playlist, Track}

import scala.concurrent.Future

abstract class DataPersistence extends StrictLogging {
  def persist(playlist: Playlist): Unit
  def persist(artist: Artist): Unit
  def persist(album: Album): Unit
  def persist(track: Track): Unit

  def persistOrchestration(orchestration: OrchestrationSummary): Future[Boolean]
  def removeOrchestration(orchestration: OrchestrationSummary): Future[Boolean]

  def getAlbumsForArtist(artistId: String): Future[Seq[Album]]
  def getOrchestrations: Future[Seq[OrchestrationSummary]]

  def deleteData(): Future[Boolean]
}