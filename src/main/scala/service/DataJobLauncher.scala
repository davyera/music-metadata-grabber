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

  /** Performs a full lyrics-scraping job for a given
   *  @return number of successful payloads (songs)
   */
  def launchGeniusLyricsJob(artistName: String): Future[Unit] = {
    // first, we need to query to find the artist ID
    queryGeniusArtistId(artistName).map { artistId: Int =>

      // then, perform a Genius request for all of the artist's songs
      genius.requestArtistSongs(artistId).map { songsResponsePages: Seq[Future[GeniusArtistSongsPage]] =>

        // iterate through each paged response
        songsResponsePages.foreach(_.map { page: GeniusArtistSongsPage =>

          // pull the set of songs from the page of results
          val songs = page.response.songs
          songs.foreach { song =>

            // push finalized results
            geniusLyrics.scrapeLyrics(song.url)
              .map(handleSongLyrics(song, artistId, artistName, _))
          }
        })
      }
    }
  }

  /** Performs a Genius search request to extract an artist's ID
   *  @return Genius ID for artist (or -1 if not found)
   */
  private[service] def queryGeniusArtistId(artistName: String): Future[Int] = {
    // initiate a search to find artist's ID -- search with one result
    genius.requestSearchPage(artistName, 1).map { searchResult: GeniusSearchResponse =>
      val hits = searchResult.response.hits
      if (hits.isEmpty)
        throw new Exception(s"No search results for artist name $artistName") // TODO specialized exception?
      else
        hits.head.result.primary_artist.id
    }
  }

  private def handleSongLyrics(song: GeniusSong, artistId: Int, artistName: String, lyrics: String): Unit = {
    val data = Lyrics(lyrics, song.id, song.title, artistId, artistName, song.url)
    logger.info(s"Received Data: $data")
    receiver.receive(data)
  }
}