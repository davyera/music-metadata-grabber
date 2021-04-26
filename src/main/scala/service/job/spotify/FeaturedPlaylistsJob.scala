package service.job.spotify

import models.ModelTransform
import models.api.db.{Playlist, Track}
import models.api.resources.spotify.{SpotifyFeaturedPlaylists, SpotifyPlaylistInfo}
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Requests track data for all Spotify featured playlists and pushes playlist data.
 *  Spawns PlaylistTracksJobs and returns a Seq of all tracks found.
 *  If [[pushTrackData]] is true, will push individual track data as well.
 */
case class FeaturedPlaylistsJob(pushPlaylistData: Boolean,
                                pushTrackData: Boolean)
                               (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Map[Playlist, Seq[Track]]] {

  override private[job] val jobName = "FEATURED_PLAYLISTS"

  override private[job] def work: Future[Map[Playlist, Seq[Track]]] = {
    // make request to Spotify to grab all featured playlists (returned as pages)
    spotify.requestFeaturedPlaylists().map { playlistPages: Seq[Future[SpotifyFeaturedPlaylists]] =>
      val playlistTrackMaps = workOnPages(playlistPages) { page: SpotifyFeaturedPlaylists =>
        logInfo(s"Received page of featured playlists. Count: ${page.playlists.items.size}")

        page.playlists.items.map { plistInfo: SpotifyPlaylistInfo =>
          // launch new Job for this playlist to grab its tracks
          val tracksJob = PlaylistTracksJob(plistInfo, pushTrackData)

          // once we have finished querying all tracks, we can send the full playlist data out
          tracksJob.doWork().map { tracks =>
            val playlist = ModelTransform.playlist(plistInfo, tracks.map(_._id))
            if (pushPlaylistData) receiver.receive(playlist)

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