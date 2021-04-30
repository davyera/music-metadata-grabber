package service.job.spotify

import models.ModelTransform
import models.api.db.Artist
import models.api.resources.spotify.SpotifyArtist
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Data retrieval job for an Artist based on their ID. */
case class ArtistJob(artistId: String, pushArtistData: Boolean)
                    (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Artist] {

  override private[job] val jobName = "ARTIST"

  override private[job] def work: Future[Artist] =
    spotify.requestArtist(artistId).map { sArtist: SpotifyArtist =>
      val artist = ModelTransform.artist(sArtist)
      if (pushArtistData) receiver.receive(artist)
      artist
    }

  override private[job] val canRecover: Boolean = false
  override private[job] def recovery: Artist = null
}
