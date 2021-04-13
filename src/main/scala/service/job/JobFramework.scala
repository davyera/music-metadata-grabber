package service.job

import models.Backend
import service.DataReceiver
import service.request.genius.{GeniusAuthTokenProvider, GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.{SpotifyAuthTokenProvider, SpotifyRequester}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.ExecutionContext

class JobFramework(implicit val context: ExecutionContext) {
  implicit val backend: Backend = AsyncHttpClientFutureBackend()(context)

  private val spotifyAuth: SpotifyAuthTokenProvider = new SpotifyAuthTokenProvider()
  implicit val spotify: SpotifyRequester = new SpotifyRequester(spotifyAuth)

  private val geniusAuth: GeniusAuthTokenProvider = new GeniusAuthTokenProvider()
  implicit val genius: GeniusRequester = new GeniusRequester(geniusAuth)
  implicit val geniusScraper: GeniusLyricsScraper = new GeniusLyricsScraper()

  implicit val receiver: DataReceiver = new DataReceiver()
}
