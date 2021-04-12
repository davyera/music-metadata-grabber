package service.job.spotify

import models.api.response.{SpotifyFeaturedPlaylists, SpotifyPlaylistInfo}
import models.db.{Playlist, Track}
import service.DataReceiver
import service.job.SpotifyJob
import service.request.spotify.SpotifyRequester

import scala.concurrent.{ExecutionContext, Future}

/** Requests track data for all Spotify featured playlists and pushes playlist data.
 *  Spawns PlaylistTracksJobs and returns a Seq of all tracks found.
 *  If [[pushTrackData]] is true, will push individual track data as well.
 */
case class FeaturedPlaylistsDataJob(pushTrackData: Boolean = false)
                                   (implicit val spotify: SpotifyRequester,
                                    implicit override val context: ExecutionContext,
                                    implicit override val receiver: DataReceiver)
  extends SpotifyJob[Map[Playlist, Seq[Track]]] {

  private[job] override def work: Future[Map[Playlist, Seq[Track]]] = {
    // make request to Spotify to grab all featured playlists (returned as pages)
    spotify.requestFeaturedPlaylists().map { playlistPages: Seq[Future[SpotifyFeaturedPlaylists]] =>
      val playlistTrackMaps = workOnPages(playlistPages) { page: SpotifyFeaturedPlaylists =>
        logInfo(s"Received page of featured playlists. Count: ${page.playlists.items.size}")

        page.playlists.items.map { plistInfo: SpotifyPlaylistInfo =>
          // launch new Job for this playlist to grab its tracks
          val tracksJob = PlaylistTracksJob(plistInfo, pushTrackData)

          // once we have finished querying all tracks, we can send the full playlist data out
          tracksJob.doWork().map { tracks =>
            val trackIds = tracks.map(_.id)
            val playlist = Playlist(plistInfo.id, plistInfo.name, plistInfo.description, trackIds)
            sendData(playlist)

            // return as a tuple so we can build the map afterwards
            playlist -> tracks
          }
        }
      }

      // Wait for all playlists and track queries to complete and build the map Playlist -> Seq[Track]
      awaitPagedResults(playlistTrackMaps).map(awaitResult).toMap
    }
  }
}