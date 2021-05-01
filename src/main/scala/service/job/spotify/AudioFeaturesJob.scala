package service.job.spotify

import models.api.db.Track
import models.api.resources.spotify.{SpotifyAudioFeatures, SpotifyAudioFeaturesPage}
import service.job.{JobEnvironment, JobException, SpotifyJob}

import scala.concurrent.Future

/** Requests Spotify audio features for the given seq of [[Track]] objects and copies audio features into them.
 *  Optionally pushes completed [[Track]] data.
 *  @return Seq of [[Track]] objects with audio features.
 */
case class AudioFeaturesJob(tracks: Seq[Track],
                            pushTrackData: Boolean,
                            featuresRequestLimit: Int = 50)
                           (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Track]] {

  override private[job] val jobName = "AUDIO_FEATURES"

  override private[job] def work: Future[Seq[Track]] = {
    val tracksData = tracks.grouped(featuresRequestLimit).toSeq.map { chunkedTracks: Seq[Track] =>
      val ids = chunkedTracks.map(_._id)
      spotify.requestAudioFeatures(ids).map { featuresResponse: SpotifyAudioFeaturesPage =>
        logInfo(s"Received page of audio features for ${featuresResponse.audio_features.size} tracks.")
        featuresResponse.audio_features.map { features: SpotifyAudioFeatures =>
          // match features with the input received
          chunkedTracks.find(_._id == features.id) match {

            // create track data using features and input track
            case Some(trk) => trk.copy(features = features.toMap)

            // if we can't back-reference the original input track, throw exception (this should be impossible)
            case None => throw JobException(s"Could not back-reference track with ID ${features.id}")
          }
        }
      }
    }

    // block until all features are finished querying so we can back-test to see if we missed any features
    val tracksWithFeatures: Seq[Track] = awaitPagedResults(tracksData)

    // check if any input tracks did not have features returned
    val tracksWithoutFeatures: Seq[Track] =
      if (tracksWithFeatures.size < tracks.size) {
        // do set operation to find the missing track IDs
        val missingIds = tracks.map(_._id).toSet &~ tracksWithFeatures.map(_._id).toSet
        logWarn(s"Could not load audio features for tracks: ${missingIds.mkString(",")}")

        // return seq of feature-less tracks
        tracks.filter(trk => missingIds.contains(trk._id))
      } else Nil

    // concatenate tracks with and without features
    val allTracks = tracksWithFeatures ++ tracksWithoutFeatures

    // finally, return and (optionally) push data
    if (pushTrackData) allTracks.foreach(data.persist)
    Future.successful(allTracks)
  }

  override private[job] def recovery: Seq[Track] = tracks
}
