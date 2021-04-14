package service.job.spotify

import models.api.response.{SpotifyAudioFeatures, SpotifyAudioFeaturesPage, SpotifyTrack}
import models.db.Track
import service.job.{JobException, JobFramework, SpotifyJob}

import scala.concurrent.Future

/** Requests Spotify audio features for the given seq of [[SpotifyTrack]] objects.
 *  Optionally pushes completed [[Track]] data.
 *
 *  @return Seq of completed [[Track]] data objects with audio features
 */
case class AudioFeaturesJob(tracks: Seq[SpotifyTrack],
                            featuresRequestLimit: Int = 50,
                            pushData: Boolean = true)
                           (implicit jobFramework: JobFramework)
  extends SpotifyJob[Seq[Track]] {

  override private[job] def work: Future[Seq[Track]] = {
    val tracksData = tracks.grouped(featuresRequestLimit).toSeq.map { chunkedTracks: Seq[SpotifyTrack] =>
      val ids = chunkedTracks.map(_.id)
      spotify.requestAudioFeatures(ids).map { featuresResponse: SpotifyAudioFeaturesPage =>
        featuresResponse.audio_features.map { features: SpotifyAudioFeatures =>
          // match features with the input received
          val trackData = chunkedTracks.find(_.id == features.id) match {

            // create track data using features and input track
            case Some(trk) => toTrackData(trk, Some(features))

            // if we can't back-reference the original input track, throw exception (this should be impossible)
            case None => throw JobException(s"Could not back-reference track with ID ${features.id}")
          }
          logInfo(s"Received audio features for track ${toTag(trackData.name, trackData.id)}")
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
        val missingIds = tracks.map(_.id).toSet &~ tracksWithFeatures.map(_.id).toSet
        logInfo(s"Could not load audio features for tracks: ${missingIds.mkString(",")}")

        // create Track data objects for feature-less tracks
        val missingTracks = tracks.filter(trk => missingIds.contains(trk.id))
        missingTracks.map(toTrackData(_))
      } else Nil

    // concatenate tracks with and without features
    val allTracksData = tracksWithFeatures ++ tracksWithoutFeatures

    // finally, return and (optionally) push data
    if (pushData) allTracksData.foreach(pushData(_))
    Future.successful(allTracksData)
  }

  private def toTrackData(trk: SpotifyTrack, features: Option[SpotifyAudioFeatures] = None): Track = {
    val featureMap: Map[String, Float] = features match {
      case Some(f) => f.toMap
      case None => Map()
    }
    Track(trk.id, trk.name, trk.popularity, trk.track_number, trk.album.id, trk.artists.map(_.id), featureMap)
  }
}
