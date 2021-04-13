package service.job.spotify

import models.api.response.{SpotifyPlaylistInfo, SpotifyPlaylistTrackRef, SpotifyPlaylistTracksPage}
import models.db.Track
import service.job.{JobFramework, SpotifyJob}

import scala.concurrent.Future

/** For a given playlist, will request all tracks for that playlist from Spotify.
 *  Optionally pushes Track data (though at this point we have no track features)
 *  @return Concatenated list of all track IDs that were found once the service.job is finished.
 */
case class PlaylistTracksJob(playlist: SpotifyPlaylistInfo, pushData: Boolean = false)
                            (implicit jobFramework: JobFramework)
  extends SpotifyJob[Seq[Track]] {

  private[job] override def work: Future[Seq[Track]] = {
    spotify.requestPlaylistTracks(playlist.id).map { playlistTracksPages: Seq[Future[SpotifyPlaylistTracksPage]] =>
      val playlistTag = s"${playlist.name} (${playlist.id})" // for logging purposes

      val pagedTracks = workOnPages(playlistTracksPages) { page: SpotifyPlaylistTracksPage =>
        logInfo(s"Received page of tracks for playlist $playlistTag. Count: ${page.items.size}")
        page.items.map { trackRef: SpotifyPlaylistTrackRef =>
          val trk = trackRef.track
          val trackData = Track(trk.id, trk.name, trk.popularity, trk.track_number, trk.album.id, trk.artists.map(_.id))
          if (pushData) {
            // TODO: Pull features as well?
            sendData(trackData)
          }
          trackData
        }
      }

      // Here we block for the jobs to finish -- and flatten the sequences of track objects to return to the parent service.job
      val tracks = awaitPagedResults(pagedTracks)
      logInfo(s"Gathered ${tracks.size} tracks for playlist $playlistTag")
      tracks
    }
  }
}