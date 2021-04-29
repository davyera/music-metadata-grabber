package service.job.spotify

import models.ModelTransform
import models.api.db.{Album, Artist}
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Full data retrieval job for an Artist based on their ID.
 *  Spawns [[ArtistAlbumsJob]]s for grabbing the artist's albums.
 */
case class ArtistJob(artistId: String, pushData: Boolean)
                    (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[(Artist, Seq[Album])] {

  override private[job] val jobName = "ARTIST"

  override private[job] def work: Future[(Artist, Seq[Album])] = {
    val sArtistFuture = spotify.requestArtist(artistId)

    ArtistAlbumsJob(artistId, pushData).doWork().map { albums =>
      val sArtist = awaitResult(sArtistFuture)
      logInfo(s"Received full artist data for artist ${toTag(sArtist.name, sArtist.id)}")
      val artist = ModelTransform.artist(sArtist, albums.map(_._id))
      if (pushData) receiver.receive(artist)
      (artist, albums)
    }
  }

  override private[job] val canRecover: Boolean = false
  override private[job] def recovery: (Artist, Seq[Album]) = (null, Nil)
}
