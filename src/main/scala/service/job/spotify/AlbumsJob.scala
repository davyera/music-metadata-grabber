package service.job.spotify

import models.ModelTransform
import models.api.db.Album
import models.api.resources.spotify.{SpotifyAlbum, SpotifyAlbums}
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Request detailed information on a set of Albums by ID */
case class AlbumsJob(albumIds: Seq[String],
                     pushAlbumData: Boolean,
                     albumsRequestLimit: Int = 20)
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
          if (pushAlbumData) receiver.receive(albumData)
          albumData
        }
      }
    }
    // flatten seq[future[seq[...]]] into future[seq[...]]
    Future.sequence(groupedAlbums).map(_.flatten)
  }

  override private[job] def recovery: Seq[Album] = Nil
}