package service.job.spotify

import models.api.response.{SpotifyAlbum, SpotifyAlbums}
import models.db.Album
import service.job.{JobFramework, SpotifyJob}

import scala.concurrent.Future

/** Request detailed information on a set of Albums by ID */
case class AlbumsJob(albumIds: Seq[String], albumsRequestLimit: Int = 20, pushData: Boolean = true)
                    (implicit jobFramework: JobFramework)
  extends SpotifyJob[Seq[Album]] {

  override private[job] def work: Future[Seq[Album]] = {
    // we are bounded by Spotify's limit of albums per request
    val groupedAlbums = albumIds.grouped(albumsRequestLimit).toSeq.map { chunkedAlbumIds: Seq[String] =>
      spotify.requestAlbums(chunkedAlbumIds).map { albumsResponse: SpotifyAlbums =>
        albumsResponse.albums.map { album: SpotifyAlbum =>
          logInfo(s"Received album info for ${toTag(album.name, album.id)}")
          val albumData = Album(album.id, album.name, album.popularity, album.artists.map(_.id),
            album.tracks.items.map(_.id))
          if (pushData) {
            pushData(albumData)
          }
          albumData
        }
      }
    }

    // block on waiting for the underlying sequences of albums to return the complete set
    Future.successful(awaitPagedResults(groupedAlbums))
  }
}