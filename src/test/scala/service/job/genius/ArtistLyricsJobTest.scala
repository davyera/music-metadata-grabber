package service.job.genius

import org.mockito.Mockito._
import service.job.{JobEnvironment, JobSpec}
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}

import scala.concurrent.Future

class ArtistLyricsJobTest extends JobSpec {

  "doWorkBlocking" should "request songs for an artist then scrape their lyrics" in {
    val genius = mock[GeniusRequester]
    when(genius.requestArtistSongs(0))
      .thenReturn(Future.successful(Seq(Future(gTrkPg1), Future(gTrkPg2))))

    val logVerifier = getLogVerifier[SongLyricsJob]

    // mock lyric scraping
    val scraper = mock[GeniusLyricsScraper]
    when(scraper.scrapeLyrics("url1")).thenReturn(Future(gTrk1Lyrics))
    when(scraper.scrapeLyrics("url2")).thenReturn(Future(gTrk2Lyrics))
    when(scraper.scrapeLyrics("url3")).thenReturn(Future(gTrk3Lyrics))
    when(scraper.scrapeLyrics("url4")).thenReturn(Future.failed(new Exception("oops")))

    implicit val jobEnv: JobEnvironment = env(gRequest = genius, gScraper = scraper)

    val job = new ArtistLyricsJob("artist") { override def queryArtistId(): Future[Int] = Future(0) }
    val result = job.doWorkBlocking()

    // verify all 3 lyrics are returned
    whenReady(result("song1")) { _ shouldEqual "lyrics1" }
    whenReady(result("song2")) { _ shouldEqual "lyrics2" }
    whenReady(result("song3")) { _ shouldEqual "lyrics3" }
    whenReady(result("song4")) { lyric =>
      lyric shouldEqual ""
      logVerifier.assertLogged("ERROR IN GENIUS:SONG_LYRICS: oops")
    }
  }

  "doWork" should "return empty lyrics map if an error occurs" in {
    val genius = mock[GeniusRequester]
    when(genius.requestSearchPage("art1", 1)).thenReturn(Future.failed(new Exception("oops")))

    val artistIdLogVerifier = getLogVerifier[GeniusArtistIdJob]
    val artistLyricLogVerifier = getLogVerifier[ArtistLyricsJob]

    implicit val jobEnv: JobEnvironment = env(gRequest = genius)

    val result = ArtistLyricsJob("art1").doWork()

    whenReady(result) { lyricsMap =>
      lyricsMap shouldEqual Map()
      artistIdLogVerifier.assertLogged("ERROR IN GENIUS:ARTIST_ID:[art1] oops")
      artistLyricLogVerifier.assertLogged("GENIUS:ARTIST_LYRICS:[art1] Skipping lyrics for artist art1.")
    }
  }
}
