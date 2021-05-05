package service.job.spotify

import models.api.db.Artist
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Checks the data persistence layer to see if any artist albums are already persisted, and filters them out of the
 *  resulting sequence of Album IDs.
 *
 *  @return Filtered list of un-persisted Album IDs for input [[Artist]]
 */
case class NewAlbumFilterJob(artist: Artist)
                            (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[String]] {

  override private[job] val jobName = "NEW_ALBUM_FILTER"

  override private[job] val jobIdentifier = s"[${toTag(artist.name, artist._id)}]"

  override private[job] def work: Future[Seq[String]] = {
    jobEnvironment.dataPersistence.getAlbumsForArtist(artist._id).map { albums =>
      val persistedAlbumIds = albums.map(_._id)
      val artistAlbumIds = artist.albums
      val newAlbumIds = artistAlbumIds.filterNot(persistedAlbumIds.toSet)
      val numFiltered = artistAlbumIds.size - newAlbumIds.size
      if (numFiltered > 0) {
        val artistTag = toTag(artist.name, artist._id)
        logInfo(s"Skipping $numFiltered albums already persisted for artist $artistTag")
      }
      newAlbumIds
    }
  }

  override private[job] def recovery: Seq[String] = artist.albums
}
