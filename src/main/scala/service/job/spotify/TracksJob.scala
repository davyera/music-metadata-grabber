package service.job.spotify

import models.api.response.{SpotifyTrack, SpotifyTracks}
import models.db.Track
import service.job.{JobFramework, SpotifyJob}

import scala.concurrent.Future

/** Requests full track data for given Track IDs. Also launches sub-jobs for grabbing Spotify audio features for
 *  each track.
 *  @return completed seq of [[Track]] data, including Features
 */
case class TracksJob(trackIds: Seq[String],
                     tracksRequestLimit: Int = 50,
                     pushData: Boolean = true)
                    (implicit jobFramework: JobFramework)
  extends SpotifyJob[Seq[Track]] {

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
    AudioFeaturesJob(tracks, tracksRequestLimit, pushData).doWork()
  }
}