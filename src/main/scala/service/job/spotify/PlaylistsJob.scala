package service.job.spotify

import models.{ModelTransform, PageableWithTotal}
import models.api.db.{Playlist, Track}
import models.api.resources.spotify.{SpotifyCategoryPlaylists, SpotifyFeaturedPlaylists, SpotifyPlaylistInfo}
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

abstract class PlaylistsJob[P <: PageableWithTotal](pushPlaylistData: Boolean)
                                                    (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Playlist]] {

  override private[job] def work: Future[Seq[Playlist]] = {
    playlistPageRequest().map { playlistPages: Seq[Future[P]] =>
      val playlistPagedResults = workOnPages(playlistPages) { page: P =>
        getPlaylistPageItems(page).map { plistInfo: SpotifyPlaylistInfo =>
          logInfo(s"Received Playlist metadata for ${toTag(plistInfo.name, plistInfo.id)}")

          val playlist = ModelTransform.playlist(plistInfo, Nil, category)
          if (pushPlaylistData) data.persist(playlist)
          playlist
        }
      }

      // Wait for all playlists pages to complete
      awaitPagedResults(playlistPagedResults)
    }
  }

  override private[job] def recovery: Seq[Playlist] = Nil

  val category: Option[String] = None

  def playlistPageRequest(): Future[Seq[Future[P]]]
  def getPlaylistPageItems(page: P): Seq[SpotifyPlaylistInfo]
}

/** Requests playlist metadata for all Spotify featured playlists.
 *  Note: Will not have "tracks" data at this point.
 */
case class FeaturedPlaylistsJob(pushPlaylistData: Boolean)
                               (implicit jobEnvironment: JobEnvironment)
  extends PlaylistsJob[SpotifyFeaturedPlaylists](pushPlaylistData) {

  override private[job] val jobName = "FEATURED_PLAYLISTS"

  override def playlistPageRequest(): Future[Seq[Future[SpotifyFeaturedPlaylists]]] =
    spotify.requestFeaturedPlaylists()

  override def getPlaylistPageItems(page: SpotifyFeaturedPlaylists): Seq[SpotifyPlaylistInfo] =
    page.playlists.items
}

/** Requests playlist metadata for all Spotify playlists for given category.
 *  Note: Will not have "tracks" data at this point.
 */
case class CategoryPlaylistsJob(categoryId: String, pushPlaylistData: Boolean)
                               (implicit jobEnvironment: JobEnvironment)
  extends PlaylistsJob[SpotifyCategoryPlaylists](pushPlaylistData) {

  override private[job] val jobName = "CATEGORY_PLAYLISTS"

  override def playlistPageRequest(): Future[Seq[Future[SpotifyCategoryPlaylists]]] =
    spotify.requestCategoryPlaylists(categoryId)

  override def getPlaylistPageItems(page: SpotifyCategoryPlaylists): Seq[SpotifyPlaylistInfo] =
    page.playlists.items

  override val category: Option[String] = Some(categoryId)
}
