package service.job.spotify

import models.api.db.{Album, Track}
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Retrieves all Spotify Track data for an artist, by iterating over all of the artist's albums. */
case class ArtistTracksJob(artistId: String)
                          (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Track]] {

  override private[job] val jobName = "ARTIST_TRACKS"

  override private[job] def work: Future[Seq[Track]] = {
    // first launch an ArtistJob (retrieves artist & album data)
    // we can push data here since there will be nothing else to query for artists and albums.
    ArtistJob(artistId, pushData = true).doWork().map { case (_, albums: Seq[Album]) =>
      val trackIds = albums.flatMap(_.tracks)
      // then use all the track IDs from all albums to launch a TracksJob
      // do not push data here since we want to query for lyrics later
      TracksJob(trackIds, pushData = false).doWork()
    }.flatten
  }
}
