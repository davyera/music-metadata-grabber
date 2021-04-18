package service.job.spotify

import models.ModelTransform
import models.api.response.{SpotifyAlbum, SpotifyAlbums}
import models.db.Album
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Request detailed information on a set of Albums by ID */
case class AlbumsJob(albumIds: Seq[String], albumsRequestLimit: Int = 20, pushData: Boolean)
                    (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[Album]] {

  override private[job] val jobName = "ALBUMS"

  override private[job] def work: Future[Seq[Album]] = {
    // we are bounded by Spotify's limit of albums per request
    val groupedAlbums = albumIds.grouped(albumsRequestLimit).toSeq.map { chunkedAlbumIds: Seq[String] =>
      spotify.requestAlbums(chunkedAlbumIds).map { albumsResponse: SpotifyAlbums =>
        albumsResponse.albums.map { album: SpotifyAlbum =>
          logInfo(s"Received album info for ${toTag(album.name, album.id)}")
          val albumData = ModelTransform.album(album)
          if (pushData) pushData(albumData)
          albumData
        }
      }
    }
    // flatten seq[future[seq[...]]] into future[seq[...]]
    Future.sequence(groupedAlbums).map(_.flatten)
  }
}