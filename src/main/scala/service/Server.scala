package service

import com.typesafe.scalalogging.StrictLogging
import models._
import service.request.spotify.SpotifyAuthTokenProvider
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Server extends App with StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global
  implicit val backend: Backend = AsyncHttpClientFutureBackend()(context)
  implicit val spotifyAuthProvider: SpotifyAuthTokenProvider = new SpotifyAuthTokenProvider()

}
