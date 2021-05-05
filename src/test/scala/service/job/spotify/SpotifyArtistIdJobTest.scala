package service.job.spotify

import models.api.resources.spotify.{SpotifyArtistsSearchPage, SpotifySearch}
import org.mockito.Mockito._
import service.job.{JobEnvironment, JobException, JobSpec}
import service.request.spotify.{SpotifyRequester, SpotifySearchType}

import scala.concurrent.Future

class SpotifyArtistIdJobTest extends JobSpec {

  "doWorkBlocking" should "return id for artist" in {
    val srchResponse = SpotifySearch(None, Some(sSrchArt))
    val spotify = mock[SpotifyRequester]
    when(spotify.searchPage("artist1", SpotifySearchType.Artists, 1)).thenReturn(Future(srchResponse))

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify)
    val logVerifier = getLogVerifier[SpotifyArtistIdJob]

    val response = SpotifyArtistIdJob("artist1").doWorkBlocking()
    response shouldEqual "art1"
    logVerifier.assertLogged("SPOTIFY:ARTIST_ID:[artist1] Queried ID for artist artist1: art1")
  }

  "doWork" should "throw exception if no artist results were returned" in {
    val srchResponse = SpotifySearch(None, None)
    val spotify = mock[SpotifyRequester]
    when(spotify.searchPage("artist1", SpotifySearchType.Artists, 1)).thenReturn(Future(srchResponse))

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify)

    val response = SpotifyArtistIdJob("artist1").doWork()
    whenReady(response.failed) { error =>
      error shouldBe a [JobException]
      error.getMessage shouldEqual "Could not find Artist ID for artist1"
    }
  }

  "doWork" should "throw exception if sequence of artists was empty" in {
    val srchResponse = SpotifySearch(None, Some(SpotifyArtistsSearchPage(Nil, 0)))
    val spotify = mock[SpotifyRequester]
    when(spotify.searchPage("artist1", SpotifySearchType.Artists, 1)).thenReturn(Future(srchResponse))

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify)

    val response = SpotifyArtistIdJob("artist1").doWork()
    whenReady(response.failed) { error =>
      error shouldBe a [JobException]
      error.getMessage shouldEqual "Could not find Artist ID for artist1"
    }
  }
}
