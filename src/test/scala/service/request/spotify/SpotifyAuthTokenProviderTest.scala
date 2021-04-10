package service.request.spotify

import models.Backend
import sttp.client.HttpError
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import testutils.UnitSpec
import utils.Configuration

class SpotifyAuthTokenProviderTest extends UnitSpec {
  private implicit val backend: Backend = AsyncHttpClientFutureBackend()

  private def getLogVerifier = getLogVerifier[SpotifyAuthTokenProvider](classOf[SpotifyAuthTokenProvider])

  "getSpotifyAuthToken" should "retrieve a valid Token" in {
    val provider = new SpotifyAuthTokenProvider()
    val logVerifier = getLogVerifier
    // use default params
    whenReady(provider.getAuthTokenString) { token =>
      token shouldNot equal("") // token should not be an empty string
      logVerifier.assertLogged(0, "Valid Auth response!")
      logVerifier.assertLogCount(1)
    }
  }

  "getSpotifyAuthToken" should "Retry 3 times then fail with bad client details" in {
    implicit val mockConfig: Configuration = new Configuration {
      override lazy val spotifyClientId: String = "aaa" // fake client id should fail
      override lazy val httpRequestRetryTimeMS: Int = 100
    }
    val provider = new SpotifyAuthTokenProvider()
    val logVerifier = getLogVerifier
    whenReady(provider.getAuthTokenString.failed) { error =>
      error shouldBe a [HttpError]
      logVerifier.assertLogged(0, "Got invalid Auth response.")
      logVerifier.assertLogged(1, "Waiting 100ms then retrying request... tries left: 3")
      logVerifier.assertLogged(2, "Got invalid Auth response.")
      logVerifier.assertLogged(3, "Waiting 100ms then retrying request... tries left: 2")
      logVerifier.assertLogged(4, "Got invalid Auth response.")
      logVerifier.assertLogged(5, "Waiting 100ms then retrying request... tries left: 1")
      logVerifier.assertLogged(6, "Got invalid Auth response.")
      logVerifier.assertLogged(7, "Could not finish request.")
      logVerifier.assertLogCount(8)
    }
  }
}
