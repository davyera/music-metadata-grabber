package service

import models.api.response._
import models.db.{Lyrics, Playlist, Track}
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
    whenReady(job.launchGeniusArtistIdJob(artist))(id => id shouldEqual expectedId)
  }

  "queryGeniusArtistId" should "throw an exception when there were no search hits" in {
    val artist = "XXX"
    val response = constructSearchResponse(Nil)

    val geniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestSearchPage(artist, 1)).thenReturn(Future.successful(response))

    val job = new TestJob(gRequest = geniusRequester)

    whenReady(job.launchGeniusArtistIdJob(artist).failed) { error =>
      error shouldBe a [JobException]
      error.getMessage shouldEqual "GENIUS: No search results for artist name XXX"
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
  private val songsPg1 = GeniusArtistSongsPage(GeniusArtistSongs(Seq(song1, song2), None))
  private val songsPg2 = GeniusArtistSongsPage(GeniusArtistSongs(Seq(song3), None))

  "launchGeniusLyricsJob" should "request songs for an artist then scrape their lyrics" in {
    // mock search returning artist id
    val searchResponse = constructSearchResponse(Seq(
      GeniusSearchHit(GeniusSearchSong(song1.id, song1.title, song1.url, GeniusSearchArtist(artistId, artist)))))

    // mock request returning artist songs
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
    job.orchestrateLyricsJobs(artist)

    // verify (with some small timeout) that our 3 songs were received
    verify(receiver, Mockito.timeout(1000).times(3)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(song1Data) shouldBe true
    capturedTracks.contains(song2Data) shouldBe true
    capturedTracks.contains(song3Data) shouldBe true
  }


  private val art1 = SpotifyArtistRef("artist1", "art1")
  private val art2 = SpotifyArtistRef("artist2", "art2")
  private val art3 = SpotifyArtistRef("artist3", "art3")
  private val alb1 = SpotifyAlbumRef("album1", "alb1")
  private val alb2 = SpotifyAlbumRef("album2", "alb2")

  private val trk1 = SpotifyTrack("t1", "song1", Seq(art1, art2), alb1, 1, 10)
  private val trk1d = Track("t1", "song1", 10, 1, "alb1", Seq("art1", "art2"), Map())
  private val trk2 = SpotifyTrack("t2", "song2", Seq(art2), alb2, 5, 40)
  private val trk2d = Track("t2", "song2", 40, 5, "alb2", Seq("art2"), Map())
  private val trk3 = SpotifyTrack("t3", "song3", Seq(art3), alb2, 1, 100)
  private val trk3d = Track("t3", "song3", 100, 1, "alb2", Seq("art3"), Map())
  private val trk4 = SpotifyTrack("t4", "song4", Seq(art1), alb1, 2, 90)
  private val trk4d = Track("t4", "song4", 90, 2, "alb1", Seq("art1"), Map())

  private val plist1 = SpotifyPlaylistInfo("plist1", "p1", "good playlist")
  private val plist1d = Playlist("p1", "plist1", "good playlist", Seq("t1", "t2"))
  private val plist2 = SpotifyPlaylistInfo("plist2", "p2", "bad playlist")
  private val plist2d = Playlist("p2", "plist2", "bad playlist", Seq("t3"))
  private val plist3 = SpotifyPlaylistInfo("plist3", "p3", "ok playlist")
  private val plist3d = Playlist("p3", "plist3", "ok playlist", Seq("t4"))

  private val fPlistPg1 = SpotifyFeaturedPlaylists("hi", SpotifyPlaylistPage(Seq(plist1, plist2), 3))
  private val fPlistPg2 = SpotifyFeaturedPlaylists("hi", SpotifyPlaylistPage(Seq(plist3), 3))

  private val p1TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk1)), 2)
  private val p1TrksPg2 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk2)), 2)
  private val p2TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk3)), 1)
  private val p3TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk4)), 1)

  "launchFeaturedPlaylistsDataJob" should "push playlist and track data" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestFeaturedPlaylists())
      .thenReturn(Future(Seq(Future(fPlistPg1), Future(fPlistPg2))))
    when(spotify.requestPlaylistTracks("p1"))
      .thenReturn(Future(Seq(Future(p1TrksPg1), Future(p1TrksPg2))))
    when(spotify.requestPlaylistTracks("p2"))
      .thenReturn(Future(Seq(Future(p2TrksPg1))))
    when(spotify.requestPlaylistTracks("p3"))
      .thenReturn(Future(Seq(Future(p3TrksPg1))))

    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])

    val job = new TestJob(sRequest = spotify, dReceiver = receiver)

    job.launchFeaturedPlaylistsDataJob(true)
    // want 7 invocations, 3 playlists, 4 tracks
    verify(receiver, Mockito.timeout(1000).times(7)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(plist1d) shouldBe true
    capturedTracks.contains(plist2d) shouldBe true
    capturedTracks.contains(plist3d) shouldBe true
    capturedTracks.contains(trk1d) shouldBe true
    capturedTracks.contains(trk2d) shouldBe true
    capturedTracks.contains(trk3d) shouldBe true
    capturedTracks.contains(trk4d) shouldBe true
  }
}
