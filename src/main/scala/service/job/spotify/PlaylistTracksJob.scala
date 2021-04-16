package service.job.spotify

import models.ModelTransform
import models.api.response.{SpotifyPlaylistInfo, SpotifyPlaylistTracksPage}
import models.db.Track
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** For a given playlist, will request all tracks for that playlist from Spotify.
 *  Optionally pushes Track data, which requires launch of [[AudioFeaturesJob]]
 *  @return Concatenated list of all track IDs that were found once the service.job is finished.
 */
case class PlaylistTracksJob(playlist: SpotifyPlaylistInfo, pushTrackData: Boolean = false)
                            (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Track]] {

  override private[job] val jobName: String = "PLAYLIST_TRACKS"

  override private[job] def work: Future[Seq[Track]] = {
    spotify.requestPlaylistTracks(playlist.id).map { playlistTracksPages: Seq[Future[SpotifyPlaylistTracksPage]] =>
      val playlistTag = toTag(playlist.name, playlist.id) // for logging purposes

      val pagedTracks = workOnPages(playlistTracksPages) { page: SpotifyPlaylistTracksPage =>
        logInfo(s"Received page of tracks for playlist $playlistTag. Count: ${page.items.size}")
        val sTrks = page.items.map(_.track)
        if (pushTrackData)
          awaitResult(AudioFeaturesJob(sTrks).doWork())
        else
          sTrks.map(ModelTransform.track(_, None)) // no features map, no worries
      }

      // Here we block for the jobs to finish -- and flatten the sequences of track objects to return to the parent service.job
      val tracks = awaitPagedResults(pagedTracks)
      logInfo(s"Gathered ${tracks.size} tracks for playlist $playlistTag")
      tracks
    }
  }
}