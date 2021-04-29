package service.job.spotify

import models.api.db.Track
import models.api.resources.spotify.{SpotifyTrack, SpotifyTracks}
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Requests full track data for given Track IDs. Also launches sub-jobs for grabbing Spotify audio features for
 *  each track.
 *  @return completed seq of [[Track]] data, including Features
 */
case class TracksJob(trackIds: Seq[String],
                     pushData: Boolean,
                     tracksRequestLimit: Int = 50)
                    (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Track]] {

  override private[job] val jobName: String = "TRACKS"

  override private[job] def work: Future[Seq[Track]] = {
    // we are bounded by Spotify's limit of tracks per request
    val groupedTracks = trackIds.grouped(tracksRequestLimit).toSeq.map { chunkedTrackIds: Seq[String] =>
      // simply return all track responses here
      spotify.requestTracks(chunkedTrackIds).map { tracksResponse: SpotifyTracks =>
        tracksResponse.tracks
      }
    }

    // block for completion of track requests so we can push the set into the features job for completion
    val tracks: Seq[SpotifyTrack] = awaitPagedResults(groupedTracks)
    AudioFeaturesJob(tracks, pushData, tracksRequestLimit).doWork()
  }

  override private[job] def recovery: Seq[Track] = Nil
}
