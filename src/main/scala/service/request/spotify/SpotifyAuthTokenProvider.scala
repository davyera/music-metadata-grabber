package service.request.spotify

import models.api.resources.spotify.SpotifyAccessToken
import models.{Backend, Request}
import service.request.AuthTokenProvider
import sttp.client.UriContext
import sttp.client.circe.asJson
import sttp.model.MediaType
import utils.Configuration

import scala.concurrent.ExecutionContext

class SpotifyAuthTokenProvider(implicit override val backend: Backend,
                               implicit override val context: ExecutionContext,
                               implicit override val config: Configuration = Configuration)
  extends AuthTokenProvider {

  private val authUri = uri"https://accounts.spotify.com/api/token"

  private val clientId: String = config.spotifyClientId
  private val secretId: String = config.spotifySecretId
  private val baseRequest: Request[SpotifyAccessToken] = sttp.client.basicRequest
    .post(authUri)
    .contentType(MediaType.ApplicationXWwwFormUrlencoded)
    .response(asJson[SpotifyAccessToken])
    .header("Access-Control-Allow-Origin", "*")
    .body(("grant_type", "client_credentials"))

  protected type T = SpotifyAccessToken
  override def requestWithAuth: Request[SpotifyAccessToken] =
    baseRequest.auth.basic(clientId, secretId)
}
