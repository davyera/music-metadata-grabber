package service.request.genius

import models.api.response.{GeniusArtistSongs, GeniusArtistSongsResponse, GeniusSong}
import testutils.APIRequesterSpec

class GeniusRequesterTest extends APIRequesterSpec {

  private implicit val tokenProvider: GeniusAuthTokenProvider = new GeniusAuthTokenProvider()
  val requester = new GeniusRequester()

  "requestArtistSongs" should "return a sequence of artist tracks" in {
    val artistId = 992709
    val response = requester.requestArtistSongs(artistId)
    verifyPages(response) { page: GeniusArtistSongsResponse =>
      page.response.songs.foreach { song: GeniusSong =>
        song.id should be > 0
        song.title shouldNot be ('empty)
        song.url should startWith ("http")
      }
    }
  }
}
