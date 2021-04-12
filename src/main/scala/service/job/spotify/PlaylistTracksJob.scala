package service.job.spotify

import models.api.response.{SpotifyPlaylistInfo, SpotifyPlaylistTrackRef, SpotifyPlaylistTracksPage}
import models.db.Track
import service.DataReceiver
import service.job.SpotifyJob
import service.request.spotify.SpotifyRequester

import scala.concurrent.{ExecutionContext, Future}

/** For a given playlist, will request all tracks for that playlist from Spotify.
 *  Optionally pushes Track data (though at this point we have no track features)
 *  @return Concatenated list of all track IDs that were found once the service.job is finished.
 */
case class PlaylistTracksJob(playlist: SpotifyPlaylistInfo, pushData: Boolean = false)
                            (implicit val spotify: SpotifyRequester,
                             implicit override val context: ExecutionContext,
                             implicit override val receiver: DataReceiver)
  extends SpotifyJob[Seq[Track]] {

  private[job] override def work: Future[Seq[Track]] = {
    spotify.requestPlaylistTracks(playlist.id).map { playlistTracksPages: Seq[Future[SpotifyPlaylistTracksPage]] =>
      val pagedTracks: Seq[Future[Seq[Track]]] = workOnPages(playlistTracksPages) { page: SpotifyPlaylistTracksPage =>
        logInfo(s"Received page of tracks for playlist ${playlist.name} (${playlist.id}). Count: ${page.items.size}")
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
      logInfo(s"Gathered ${tracks.size} tracks for playlist ${playlist.name} (${playlist.id})")
      tracks
    }
  }
}