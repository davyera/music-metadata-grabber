package service.request.genius

import models.{Backend, HardcodedToken, Request}
import service.AuthTokenProvider
import sttp.client.UriContext
import sttp.client.circe.asJson
import sttp.model.MediaType
import utils.Configuration

import scala.concurrent.{ExecutionContext, Future}

class GeniusAuthTokenProvider(implicit override val backend: Backend,
                              implicit override val context: ExecutionContext,
                              implicit override val config: Configuration = Configuration)
  extends AuthTokenProvider[HardcodedToken] {

  // TODO BELOW: UNFINISHED AUTH IMPLEMENTATION. JUST RELYING ON HARDCODED TOKEN IN CONFIG FOR NOW
  private val authUri = uri"https://api.genius.com/oauth/authorize"
  private val clientId: String = config.geniusClientId
  private val secretId: String = config.geniusSecretId
  private val baseRequest: Request[HardcodedToken] = sttp.client.basicRequest
    .post(authUri)
    .contentType(MediaType.ApplicationXWwwFormUrlencoded)
    .response(asJson[HardcodedToken])
    .header("Access-Control-Allow-Origin", "*")
  private val bodyParams = Map(
    "code"            -> "TODO",                 // not implemented!
    "client_secret"   -> secretId,
    "grant_type"      -> "authorization_code",
    "client_id"       -> clientId,
    "redirect_uri"    -> "TODO",                 // not implemented!
    "response_type"   -> "code"
  )
  override val requestWithAuth: Request[HardcodedToken] = baseRequest.body(bodyParams)
  // TODO ABOVE: UNFINISHED AUTH IMPLEMENTATION. JUST RELYING ON HARDCODED TOKEN IN CONFIG FOR NOW

  private lazy val hardcodedToken = Future.successful(HardcodedToken(config.geniusAuthToken))
  override def getAuthToken: Future[HardcodedToken] = hardcodedToken
}
