package service

import com.typesafe.scalalogging.StrictLogging
import models.Backend
import models.api.response.{GeniusArtistSongsPage, GeniusSearchResponse, GeniusSong}
import models.db.Lyrics
import service.request.genius.{GeniusAuthTokenProvider, GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.{SpotifyAuthTokenProvider, SpotifyRequester}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{ExecutionContext, Future}

class DataJobLauncher(implicit val context: ExecutionContext) extends StrictLogging {

  private implicit val backend: Backend = AsyncHttpClientFutureBackend()(context)

  private val spotifyAuth: SpotifyAuthTokenProvider = new SpotifyAuthTokenProvider()
  private[service] val spotify: SpotifyRequester = new SpotifyRequester(spotifyAuth)

  private val geniusAuth: GeniusAuthTokenProvider = new GeniusAuthTokenProvider()
  private[service] val genius: GeniusRequester = new GeniusRequester(geniusAuth)
  private[service] val geniusLyrics: GeniusLyricsScraper = new GeniusLyricsScraper()

  private[service] val receiver: DataReceiver = new DataReceiver()

  def launchSpotifyFeaturedPlaylistsJob(): Future[Unit] = {
    // pull featured playlists

    // query each playlist for its tracks

    // for each track, query the artist

    // for each artist, query their albums

    // for each album, query its tracks

    // for each track, query its features
    Future.successful()
  }

  /** Performs a full lyrics-scraping job for a given artist
   */
  def orchestrateLyricsJobs(artistName: String): Future[Unit] = {
    // first, we need to query to find the artist ID
    launchGeniusArtistIdJob(artistName).map { artistId: Int =>
      // then, perform a Genius request for all of the artist's songs
      launchGeniusArtistLyricsJob(artistId, artistName)
    }
  }

  /** Performs a Genius search request to extract an artist's ID
   *  @return Genius ID for artist (or -1 if not found)
   */
  private[service] def launchGeniusArtistIdJob(artistName: String): Future[Int] = {
    // initiate a search to find artist's ID -- search with one result
    genius.requestSearchPage(artistName, 1).map { searchResult: GeniusSearchResponse =>
      val hits = searchResult.response.hits
      if (hits.isEmpty)
        throw JobException(s"GENIUS: No search results for artist name $artistName")
      else {
        val id = hits.head.result.primary_artist.id
        logger.info(s"GENIUS: Queried ID for artist $artistName: $id")
        id
      }
    }
  }

  /** Processes scraping all lyrics for a given artist (given an artist ID that has already been queried)
   */
  private def launchGeniusArtistLyricsJob(artistId: Int, artistName: String): Future[Unit] = {
    genius.requestArtistSongs(artistId).map { songsResponsePages: Seq[Future[GeniusArtistSongsPage]] =>

      // iterate through each paged response
      songsResponsePages.foreach(_.map { page: GeniusArtistSongsPage =>
        logger.info(s"GENIUS: Received page of ${page.response.songs.size} songs for artist $artistName ($artistId)")

        // pull the set of songs from the page of results
        val songs = page.response.songs
        songs.foreach { song =>
          launchGeniusSongLyricsJob(song, artistName, artistId)
        }
      })
    }
  }

  /** Scrapes Genius for the given song. Requires artist and artist ID info for the data push.
   */
  private def launchGeniusSongLyricsJob(song: GeniusSong, artist: String, artistId: Int): Future[Unit] = {
    geniusLyrics.scrapeLyrics(song.url).map { lyrics: String =>
      val lyricsData = Lyrics(lyrics, song.id, song.title, artistId, artist, song.url)
      receiver.receive(lyricsData)
    }
  }
}

case class JobException(msg: String) extends Exception(msg)