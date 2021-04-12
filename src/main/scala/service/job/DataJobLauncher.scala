package service.job

import com.typesafe.scalalogging.StrictLogging
import models.Backend
import service.DataReceiver
import service.job.genius.{ArtistIdJob, ArtistLyricsJob}
import service.request.genius.{GeniusAuthTokenProvider, GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.{SpotifyAuthTokenProvider, SpotifyRequester}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{ExecutionContext, Future}

class DataJobLauncher(implicit val context: ExecutionContext) extends StrictLogging {

  private implicit val backend: Backend = AsyncHttpClientFutureBackend()(context)

  private val spotifyAuth: SpotifyAuthTokenProvider = new SpotifyAuthTokenProvider()
  private[service] implicit val spotify: SpotifyRequester = new SpotifyRequester(spotifyAuth)

  private val geniusAuth: GeniusAuthTokenProvider = new GeniusAuthTokenProvider()
  private[service] implicit val genius: GeniusRequester = new GeniusRequester(geniusAuth)
  private[service] implicit val geniusLyrics: GeniusLyricsScraper = new GeniusLyricsScraper()

  private[service] implicit val receiver: DataReceiver = new DataReceiver()

  def orchestratePlaylistDataJobs(): Future[Unit] = {
    // pull featured playlists

    // query each playlist for its tracks

    // for each track, query the artist

    // for each artist, query their albums

    // for each album, query its tracks

    // for each track, query its features
    Future.successful()
  }

  /** Performs a full lyrics-scraping service.job for a given artist
   */
  def orchestrateLyricsJobs(artistName: String): Future[Unit] = {
    // first, we need to query to find the artist ID
    ArtistIdJob(artistName).doWork().map { artistId: Int =>
      // then, perform a Genius request for all of the artist's songs
      ArtistLyricsJob(artistId, artistName).doWork()
    }
  }
}