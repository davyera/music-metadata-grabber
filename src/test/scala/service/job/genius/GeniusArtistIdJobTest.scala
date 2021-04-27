package service.job.genius

import org.mockito.Mockito.when
import service.job.{JobException, JobEnvironment, JobSpec}
import service.request.genius.GeniusRequester

import scala.concurrent.Future

class GeniusArtistIdJobTest extends JobSpec {

  "doWork" should "return a successful future of an ID when successful" in {
    val response = mkGeniusSearchResponse(Seq(gSrchHt1, gSrchHt2))

    val geniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage("artist1", 1)).thenReturn(Future.successful(response))

    implicit val jobEnv: JobEnvironment = env(gRequest = geniusRequester)
    val result = GeniusArtistIdJob("artist1").doWork()

    whenReady(result)(_ shouldEqual 0)
  }

  "doWork" should "throw an exception when there were no search hits" in {
    val response = mkGeniusSearchResponse(Nil)

    val geniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage("XXX", 1)).thenReturn(Future.successful(response))

    implicit val jobEnv: JobEnvironment = env(gRequest = geniusRequester)
    val result = GeniusArtistIdJob("XXX").doWork()

    whenReady(result.failed) { error =>
      error shouldBe a [JobException]
      error.getMessage shouldEqual "No search results for artist name XXX"
    }
  }

  "doWork" should "throw an exception when where was a search hit for a different artist" in {
    val response = mkGeniusSearchResponse(Seq(gSrchHt2))

    val geniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage("artist1", 1)).thenReturn(Future.successful(response))

    implicit val jobEnv: JobEnvironment = env(gRequest = geniusRequester)
    val result = GeniusArtistIdJob("artist1").doWork()

    whenReady(result.failed) { error =>
      error shouldBe a [JobException]
      error.getMessage shouldEqual "No valid ID for artist artist1"
    }
  }
}
