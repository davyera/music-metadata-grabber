package service

import models.api.response._
import models.db.Lyrics
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito._
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.SpotifyRequester
import testutils.UnitSpec

import scala.concurrent.Future

class DataJobLauncherTest extends UnitSpec {

  class TestJob(scraper: GeniusLyricsScraper = null,
                gRequest: GeniusRequester = null,
                sRequest: SpotifyRequester = null,
                dReceiver: DataReceiver = null) extends DataJobLauncher {
    override val geniusLyrics: GeniusLyricsScraper = scraper
    override val genius: GeniusRequester = gRequest
    override val spotify: SpotifyRequester = sRequest
    override val receiver: DataReceiver = dReceiver
  }
  private def constructSearchResponse(hits: Seq[GeniusSearchHit]) = GeniusSearchResponse(GeniusSearchHits(hits, None))

  "queryGeniusArtistId" should "return a successful future of an ID when successful" in {
    val artist = "mock-artist"
    val expectedId = 2
    val response = constructSearchResponse(Seq(
      GeniusSearchHit(GeniusSearchSong(1, "song123", "www.genius1.com", GeniusSearchArtist(expectedId, artist))),
      GeniusSearchHit(GeniusSearchSong(100, "song456", "www.genius2.com", GeniusSearchArtist(3, "zzz")))
    ))

    val geniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage(artist, 1)).thenReturn(Future.successful(response))

    val job = new TestJob(gRequest = geniusRequester)
    whenReady(job.queryGeniusArtistId(artist))(id => id shouldEqual expectedId)
  }

  "queryGeniusArtistId" should "throw an exception when there were no search hits" in {
    val artist = "XXX"
    val response = constructSearchResponse(Nil)

    val geniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage(artist, 1)).thenReturn(Future.successful(response))

    val job = new TestJob(gRequest = geniusRequester)

    whenReady(job.queryGeniusArtistId(artist).failed) { error =>
      error shouldBe an [Exception]
      error.getMessage shouldEqual("No search results for artist name XXX")
    }
  }

  private val artist = "david bowie"
  private val artistId = 123
  private val song1Data = Lyrics("la la la", 1, "bowie song", artistId, artist, "song1Url")
  private val song2Data = Lyrics("lo lo lo", 2, "bowie song 2", artistId, artist, "song2Url")
  private val song3Data = Lyrics("ha ha ha", 3, "davy song", artistId, artist, "song3Url")
  private val song1 = GeniusSong(1, song1Data.trackName, song1Data.url)
  private val song2 = GeniusSong(2, song2Data.trackName, song2Data.url)
  private val song3 = GeniusSong(3, song3Data.trackName, song3Data.url)
  "launchGeniusLyricsJob" should "request songs for an artist then scrape their lyrics" in {
    // mock search returning artist id
    val searchResponse = constructSearchResponse(Seq(
      GeniusSearchHit(GeniusSearchSong(song1.id, song1.title, song1.url, GeniusSearchArtist(artistId, artist)))))

    // mock request returning artist songs
    val songsPg1 = GeniusArtistSongsPage(GeniusArtistSongs(Seq(song1, song2), None))
    val songsPg2 = GeniusArtistSongsPage(GeniusArtistSongs(Seq(song3), None))
    val geniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage(artist, 1))
      .thenReturn(Future.successful(searchResponse))
    when(geniusRequester.requestArtistSongs(artistId))
      .thenReturn(Future.successful(Seq(Future.successful(songsPg1), Future.successful(songsPg2))))

    // mock lyric scraping
    val scraper = mock[GeniusLyricsScraper]
    when(scraper.scrapeLyrics(song1.url)).thenReturn(Future.successful(song1Data.lyrics))
    when(scraper.scrapeLyrics(song2.url)).thenReturn(Future.successful(song2Data.lyrics))
    when(scraper.scrapeLyrics(song3.url)).thenReturn(Future.successful(song3Data.lyrics))

    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])

    val job = new TestJob(scraper, geniusRequester, dReceiver = receiver)
    job.launchGeniusLyricsJob(artist)

    // verify (with some small timeout) that our 3 songs were received
    verify(receiver, Mockito.timeout(1000).times(3)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(song1Data) shouldBe true
    capturedTracks.contains(song2Data) shouldBe true
    capturedTracks.contains(song3Data) shouldBe true
  }
}
