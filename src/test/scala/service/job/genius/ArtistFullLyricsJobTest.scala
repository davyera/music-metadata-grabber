package service.job.genius

import org.mockito.Mockito._
import service.job.{JobEnvironment, JobSpec}
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}

import scala.concurrent.Future

class ArtistFullLyricsJobTest extends JobSpec {

  "doWork" should "request songs for an artist then scrape their lyrics" in {
    val genius = mock[GeniusRequester]
    // mock artist ID search
    when(genius.requestSearchPage("art1", 1))
      .thenReturn(Future(mkGeniusSearchResponse(Seq(gSrchHt1))))
    // mock songs request for the artist
    when(genius.requestArtistSongs(0))
      .thenReturn(Future.successful(Seq(Future(gTrkPg1), Future(gTrkPg2))))

    // mock lyric scraping
    val scraper = mock[GeniusLyricsScraper]
    when(scraper.scrapeLyrics("url1")).thenReturn(Future(gTrk1Lyrics))
    when(scraper.scrapeLyrics("url2")).thenReturn(Future(gTrk2Lyrics))
    when(scraper.scrapeLyrics("url3")).thenReturn(Future(gTrk3Lyrics))

    implicit val jobEnv: JobEnvironment = env(gRequest = genius, gScraper = scraper)

    val result = ArtistFullLyricsJob("art1").doWork()

    // verify all 3 lyrics are returned
    whenReady(result) { lyricMap: Map[String, Future[String]] =>
      whenReady(lyricMap("song1")) { _ shouldEqual "lyrics1" }
      whenReady(lyricMap("song2")) { _ shouldEqual "lyrics2" }
      whenReady(lyricMap("song3")) { _ shouldEqual "lyrics3" }
    }
  }
}
