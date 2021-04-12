package service.job.genius

import models.api.response.{GeniusSearchArtist, GeniusSearchHit, GeniusSearchHits, GeniusSearchResponse, GeniusSearchSong}
import org.mockito.Mockito.when
import service.DataReceiver
import service.job.JobException
import service.request.genius.GeniusRequester
import testutils.JobSpec

import scala.concurrent.Future

class ArtistIdJobTest extends JobSpec {

  implicit val receiver: DataReceiver = mock[DataReceiver]

  def constructSearchResponse(hits: Seq[GeniusSearchHit]): GeniusSearchResponse =
    GeniusSearchResponse(GeniusSearchHits(hits, None))

  "doWork" should "return a successful future of an ID when successful" in {
    val artist = "mock-artist"
    val expectedId = 2
    val response = constructSearchResponse(Seq(
      GeniusSearchHit(GeniusSearchSong(1, "song123", "www.genius1.com", GeniusSearchArtist(expectedId, artist))),
      GeniusSearchHit(GeniusSearchSong(100, "song456", "www.genius2.com", GeniusSearchArtist(3, "zzz")))
    ))

    implicit val geniusRequester: GeniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage(artist, 1)).thenReturn(Future.successful(response))

    val job = ArtistIdJob(artist)
    whenReady(job.doWork())(id => id shouldEqual expectedId)
  }

  "doWork" should "throw an exception when there were no search hits" in {
    val artist = "XXX"
    val response = constructSearchResponse(Nil)

    implicit val geniusRequester: GeniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage(artist, 1)).thenReturn(Future.successful(response))

    val job = ArtistIdJob(artist)

    whenReady(job.doWork().failed) { error =>
      error shouldBe a [JobException]
      error.getMessage shouldEqual "GENIUS: No search results for artist name XXX"
    }
  }
}
