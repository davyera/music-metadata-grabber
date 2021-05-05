package service.job.spotify

import models.api.db.Artist
import models.api.resources.spotify.SpotifyAlbumRef
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Request all albums for a given artist. Will remove albums with duplicate names.
 *  Optionally push Artist data (will be completed once all Album IDs are returned)
 *  Returns an Artist object with Album ID data.
 */
case class ArtistAlbumsJob(artist: Artist, pushArtistData: Boolean)
                          (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Artist] {

  // Max number defined by Spotify
  private val ALBUM_PAGE_LIMIT: Int = 20

  override private[job] val jobName = "ARTIST_ALBUMS"

  override private[job] val jobIdentifier = s"[${toTag(artist.name, artist._id)}]"

  override private[job] def work: Future[Artist] = {
    val artistId = artist._id
    val artistTag = toTag(artist.name, artist._id)

    // first, query all album refs for the artist
    val albumRefsFuture: Future[Seq[SpotifyAlbumRef]] =
      spotify.requestArtistAlbums(artistId, ALBUM_PAGE_LIMIT).map { albumsPages =>
        val pagedAlbumRefs = workOnPages(albumsPages) { _.items }
        awaitPagedResults(pagedAlbumRefs)
      }

    albumRefsFuture.map { albumRefs: Seq[SpotifyAlbumRef] =>
      // we need to find the distinct albums based on name (Spotify often returns duplicate albums)
      val uniqueAlbumIds = albumRefs.distinctBy(_.name).map(_.id)

      val numFiltered = albumRefs.size - uniqueAlbumIds.size
      if (numFiltered > 0) logInfo(s"Filtered duplicate albums for artist $artistTag. Count: $numFiltered")

      // launch AlbumInfo jobs with the now-unique IDs
      val finalArtist = artist.copy(albums = uniqueAlbumIds)
      if (pushArtistData) data.persist(finalArtist)
      finalArtist
    }
  }

  override private[job] def recovery: Artist = artist
}
