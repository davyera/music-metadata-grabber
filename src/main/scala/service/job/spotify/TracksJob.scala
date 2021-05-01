package service.job.spotify

import models.ModelTransform
import models.api.db.Track
import models.api.resources.spotify.SpotifyTracks
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Requests Spotify track metadata for given Track IDs.
 *  @return completed seq of [[Track]] data without audio features
 */
case class TracksJob(trackIds: Seq[String],
                     pushTrackData: Boolean,
                     tracksRequestLimit: Int = 50)
                    (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Track]] {

  override private[job] val jobName: String = "TRACKS"

  override private[job] def work: Future[Seq[Track]] = {
    // we are bounded by Spotify's limit of tracks per request
    val chunkedResults = trackIds.grouped(tracksRequestLimit).toSeq.map { chunkedTrackIds: Seq[String] =>
      spotify.requestTracks(chunkedTrackIds).map { tracksResponse: SpotifyTracks =>
        tracksResponse.tracks.map { spotifyTrack =>
          val track = ModelTransform.track(spotifyTrack)
          if (pushTrackData) data.receive(track)
          track
        }
      }
    }
    flattenChunkedResults(chunkedResults)
  }

  override private[job] def recovery: Seq[Track] = Nil
}
