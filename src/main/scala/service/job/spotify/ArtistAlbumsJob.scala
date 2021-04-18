package service.job.spotify

import models.api.response.{SpotifyAlbumRef, SpotifyArtistAlbumsPage}
import models.db.Album
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Request all albums for a given artist ID. */
case class ArtistAlbumsJob(artistId: String, pushData: Boolean)
                          (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Album]] {

  // Max number defined by Spotify
  private val ALBUM_PAGE_LIMIT: Int = 20

  override private[job] val jobName = "ARTIST_ALBUMS"

  override private[job] def work: Future[Seq[Album]] = {
    // first, query all album IDs for the artist
    spotify.requestArtistAlbums(artistId, ALBUM_PAGE_LIMIT).map { albumsPages =>
      workOnPages(albumsPages) { page: SpotifyArtistAlbumsPage =>
        logInfo(s"Received page of albums for artist $artistId. Count: ${page.items.size}")
        page.items
      }
    }.flatMap { albumIdsFuture: Seq[Future[Seq[SpotifyAlbumRef]]] =>
      // block until we receive all album reference objects for the artist
      val albumRefs = awaitPagedResults(albumIdsFuture)

      // we need to find the distinct albums based on name (Spotify often returns duplicate albums)
      val uniqueAlbumIds = albumRefs.distinctBy(_.name).map(_.id)

      val numFiltered = albumRefs.size - uniqueAlbumIds.size
      if (numFiltered > 0)
        logInfo(s"Filtered duplicate albums for artist $artistId. Count: $numFiltered")

      // launch AlbumInfo jobs with the now-unique IDs
      AlbumsJob(uniqueAlbumIds, pushData = pushData).doWork()
    }
  }
}
