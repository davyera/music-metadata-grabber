package service.job.spotify

import models.ModelTransform
import models.api.db.Track
import models.api.resources.spotify.{SpotifyPlaylistInfo, SpotifyPlaylistTracksPage}
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** For a given playlist, will request all tracks for that playlist from Spotify.
 *  Optionally pushes Track data, which requires launch of [[AudioFeaturesJob]]
 *  @return Concatenated list of all track IDs that were found once the service.job is finished.
 */
case class PlaylistTracksJob(playlist: SpotifyPlaylistInfo,
                             pushTrackData: Boolean)
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
          awaitResult(AudioFeaturesJob(sTrks, pushTrackData).doWork())
        else
          sTrks.map(ModelTransform.track(_, None)) // no features map, no worries
      }

      // Block for the jobs to finish so we can send track objects parent job
      val tracks = awaitPagedResults(pagedTracks)
      logInfo(s"Gathered ${tracks.size} tracks for playlist $playlistTag")
      tracks
    }
  }

  override private[job] def recovery: Seq[Track] = Nil
}