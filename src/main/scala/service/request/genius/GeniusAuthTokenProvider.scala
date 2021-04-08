package service.request.genius

import models.api.response.GeniusAccessToken
import models.{Backend, Request}
import service.AuthTokenProvider
import sttp.client.UriContext
import sttp.client.circe.asJson
import sttp.model.MediaType
import utils.Configuration

import scala.concurrent.{ExecutionContext, Future}

class GeniusAuthTokenProvider(implicit override val backend: Backend,
                              implicit override val context: ExecutionContext,
                              implicit override val config: Configuration = Configuration)
  extends AuthTokenProvider {

  // TODO BELOW: UNFINISHED AUTH IMPLEMENTATION. JUST RELYING ON HARDCODED TOKEN IN CONFIG FOR NOW
  private val authUri = uri"https://api.genius.com/oauth/authorize"
  private val clientId: String = config.geniusClientId
  private val secretId: String = config.geniusSecretId
  private val baseRequest = sttp.client.basicRequest
    .post(authUri)
    .contentType(MediaType.ApplicationXWwwFormUrlencoded)
    .response(asJson[GeniusAccessToken])
    .header("Access-Control-Allow-Origin", "*")
  private val bodyParams = Map(
    "code"            -> "TODO",
    "client_secret"   -> secretId,
    "grant_type"      -> "authorization_code",
    "client_id"       -> clientId,
    "redirect_uri"    -> "TODO",
    "response_type"   -> "code"
  )

  protected type T = GeniusAccessToken
  override def requestWithAuth: Request[GeniusAccessToken] = baseRequest.body(bodyParams)
  // TODO ABOVE: UNFINISHED AUTH IMPLEMENTATION. JUST RELYING ON HARDCODED TOKEN IN CONFIG FOR NOW

  // FOR NOW WE JUST USE THE HARDCODED, CONFIGURATION-SUPPLIED TOKEN
  private lazy val hardcodedToken = Future.successful(config.geniusAuthToken)
  override def getAuthTokenString: Future[String] = hardcodedToken
}
