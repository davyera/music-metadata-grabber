package service.job.spotify

import models.ModelTransform
import models.api.db.Track
import models.api.resources.spotify.{SpotifyAudioFeatures, SpotifyAudioFeaturesPage, SpotifyTrack}
import service.job.{JobEnvironment, JobException, SpotifyJob}

import scala.concurrent.Future

/** Requests Spotify audio features for the given seq of [[SpotifyTrack]] objects.
 *  Optionally pushes completed [[Track]] data.
 *
 *  @return Seq of completed [[Track]] data objects with audio features
 */
case class AudioFeaturesJob(tracks: Seq[SpotifyTrack],
                            pushData: Boolean,
                            featuresRequestLimit: Int = 50)
                           (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Track]] {

  override private[job] val jobName = "AUDIO_FEATURES"

  override private[job] def work: Future[Seq[Track]] = {
    val tracksData = tracks.grouped(featuresRequestLimit).toSeq.map { chunkedTracks: Seq[SpotifyTrack] =>
      val ids = chunkedTracks.map(_.id)
      spotify.requestAudioFeatures(ids).map { featuresResponse: SpotifyAudioFeaturesPage =>
        featuresResponse.audio_features.map { features: SpotifyAudioFeatures =>
          // match features with the input received
          val trackData = chunkedTracks.find(_.id == features.id) match {

            // create track data using features and input track
            case Some(trk) => ModelTransform.track(trk, Some(features))

            // if we can't back-reference the original input track, throw exception (this should be impossible)
            case None => throw JobException(s"Could not back-reference track with ID ${features.id}")
          }
          logInfo(s"Received audio features for track ${toTag(trackData.name, trackData._id)}")
          trackData
        }
      }
    }

    // block until all features are finished querying so we can back-test to see if we missed any features
    val tracksWithFeatures: Seq[Track] = awaitPagedResults(tracksData)

    // check if any input tracks did not have features returned
    val tracksWithoutFeatures: Seq[Track] =
      if (tracksWithFeatures.size < tracks.size) {
        // do set operation to find the missing track IDs
        val missingIds = tracks.map(_.id).toSet &~ tracksWithFeatures.map(_._id).toSet
        logInfo(s"Could not load audio features for tracks: ${missingIds.mkString(",")}")

        // create Track data objects for feature-less tracks
        val missingTracks = tracks.filter(trk => missingIds.contains(trk.id))
        missingTracks.map(ModelTransform.track(_, None))
      } else Nil

    // concatenate tracks with and without features
    val allTracks = tracksWithFeatures ++ tracksWithoutFeatures

    // finally, return and (optionally) push data
    if (pushData) allTracks.foreach(receiver.receive)
    Future.successful(allTracks)
  }

  override private[job] def recovery: Seq[Track] = Nil
}
