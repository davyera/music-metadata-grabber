package service.request.genius

import models.api.response.{GeniusArtistSongsResponse, GeniusSearchHit, GeniusSearchResponse, GeniusSong}
import testutils.APIRequesterSpec

class GeniusRequesterTest extends APIRequesterSpec {

  private implicit val tokenProvider: GeniusAuthTokenProvider = new GeniusAuthTokenProvider()
  val requester = new GeniusRequester()

  val artistIdHE = 992709
  val artistNameHE = "Hazel English"

  "requestSearchPage" should "return valid Genius search results" in {
    val response = requester.requestSearchPage(artistNameHE, 2)
    whenReady(response) { page: GeniusSearchResponse =>
      page.response.hits.foreach { hit: GeniusSearchHit =>
        val song = hit.result
        song.id should be > 0
        song.title shouldNot be ('empty)
        song.url should startWith("http")
        println(song.url)

        val artist = song.primary_artist
        artist.id shouldEqual artistIdHE
        artist.name shouldEqual artistNameHE
      }
    }
  }

  "requestArtistSongs" should "return a sequence of artist tracks" in {
    val response = requester.requestArtistSongs(artistIdHE)
    verifyPages(response) { page: GeniusArtistSongsResponse =>
      page.response.songs.foreach { song: GeniusSong =>
        song.id should be > 0
        song.title shouldNot be ('empty)
        song.url should startWith ("http")
      }
    }
  }
}
