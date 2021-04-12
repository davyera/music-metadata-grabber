package service.job.genius

import models.api.response.GeniusArtistSongsPage
import service.DataReceiver
import service.job.GeniusJob
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}

import scala.concurrent.{ExecutionContext, Future}

/** Processes scraping all lyrics for a given artist (given an artist ID that has already been queried)
 */
case class ArtistLyricsJob(artistId: Int, artistName: String)
                          (implicit val genius: GeniusRequester,
                           implicit val geniusScraper: GeniusLyricsScraper,
                           implicit override val context: ExecutionContext,
                           implicit override val receiver: DataReceiver)
  extends GeniusJob[Unit] {

  private[job] override def work: Future[Unit] = {
    genius.requestArtistSongs(artistId).map { songsResponsePages: Seq[Future[GeniusArtistSongsPage]] =>
      workOnPages(songsResponsePages) { page: GeniusArtistSongsPage =>
        logInfo(s"Received page of ${page.response.songs.size} songs for artist $artistName ($artistId)")
        page.response.songs.map { song =>
          // launch a service.job for scraping the lyrics for each song
          SongLyricsJob(song, artistName, artistId).doWork()
        }
      }
    }
  }
}
