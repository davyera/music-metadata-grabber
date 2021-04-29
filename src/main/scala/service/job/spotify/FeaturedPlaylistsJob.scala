package service.job.spotify

import models.{ModelTransform, PageableWithTotal}
import models.api.db.{Playlist, Track}
import models.api.resources.spotify.{SpotifyCategoryPlaylists, SpotifyFeaturedPlaylists, SpotifyPlaylistInfo}
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

abstract class PlaylistsJob[P <: PageableWithTotal](pushPlaylistData: Boolean,
                                                    pushTrackData: Boolean)
                                                    (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Map[Playlist, Seq[Track]]] {

  override private[job] def work: Future[Map[Playlist, Seq[Track]]] = {
    playlistPageRequest().map { playlistPages: Seq[Future[P]] =>
      val playlistTrackMaps = workOnPages(playlistPages) { page: P =>
        getPlaylistPageItems(page).map { plistInfo: SpotifyPlaylistInfo =>
          logInfo(s"Received Playlist metadata for ${toTag(plistInfo.name, plistInfo.id)}")

          // launch new Job for this playlist to grab its tracks
          val tracksJob = PlaylistTracksJob(plistInfo, pushTrackData)

          // once we have finished querying all tracks, we can send the full playlist data out
          tracksJob.doWork().map { tracks =>
            val playlist = ModelTransform.playlist(plistInfo, tracks.map(_._id), category)
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

  override private[job] def recovery: Map[Playlist, Seq[Track]] = Map()

  val category: Option[String] = None

  def playlistPageRequest(): Future[Seq[Future[P]]]
  def getPlaylistPageItems(page: P): Seq[SpotifyPlaylistInfo]
}

/** Requests track data for all Spotify featured playlists and pushes playlist data.
 *  Spawns [[PlaylistTracksJob]] jobs and returns a Seq of all tracks found.
 *  If [[pushTrackData]] is true, will push individual track data as well.
 */
case class FeaturedPlaylistsJob(pushPlaylistData: Boolean,
                                pushTrackData: Boolean)
                               (implicit jobEnvironment: JobEnvironment)
  extends PlaylistsJob[SpotifyFeaturedPlaylists](pushPlaylistData, pushTrackData) {

  override private[job] val jobName = "FEATURED_PLAYLISTS"

  override def playlistPageRequest(): Future[Seq[Future[SpotifyFeaturedPlaylists]]] =
    spotify.requestFeaturedPlaylists()

  override def getPlaylistPageItems(page: SpotifyFeaturedPlaylists): Seq[SpotifyPlaylistInfo] =
    page.playlists.items
}

/** Requests track data for all Spotify playlists for a given category. Pushes playlist data.
 *  Spawns [[PlaylistTracksJob]] jobs and returns a Seq of all tracks found.
 *  If [[pushTrackData]] is true, will push individual track data as well.
 */
case class CategoryPlaylistsJob(categoryId: String,
                                pushPlaylistData: Boolean,
                                pushTrackData: Boolean)
                               (implicit jobEnvironment: JobEnvironment)
  extends PlaylistsJob[SpotifyCategoryPlaylists](pushPlaylistData, pushTrackData) {

  override private[job] val jobName = "CATEGORY_PLAYLISTS"

  override def playlistPageRequest(): Future[Seq[Future[SpotifyCategoryPlaylists]]] =
    spotify.requestCategoryPlaylists(categoryId)

  override def getPlaylistPageItems(page: SpotifyCategoryPlaylists): Seq[SpotifyPlaylistInfo] =
    page.playlists.items

  override val category: Option[String] = Some(categoryId)
}
