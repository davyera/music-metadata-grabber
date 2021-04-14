package service.job.genius

import models.api.response.{GeniusArtistSongs, GeniusArtistSongsPage, GeniusSong}
import models.db.Lyrics
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{verify, when}
import service.DataReceiver
import service.job.{JobFramework, JobSpec}
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}

import scala.concurrent.Future

class ArtistLyricsJobTest extends JobSpec {
  private val artist = "david bowie"
  private val artistId = 123
  private val song1Data = Lyrics("la la la", 1, "bowie song", artistId, artist, "song1Url")
  private val song2Data = Lyrics("lo lo lo", 2, "bowie song 2", artistId, artist, "song2Url")
  private val song3Data = Lyrics("ha ha ha", 3, "davy song", artistId, artist, "song3Url")
  private val song1 = GeniusSong(1, song1Data.track_name, song1Data.url)
  private val song2 = GeniusSong(2, song2Data.track_name, song2Data.url)
  private val song3 = GeniusSong(3, song3Data.track_name, song3Data.url)
  private val songsPg1 = GeniusArtistSongsPage(GeniusArtistSongs(Seq(song1, song2), None))
  private val songsPg2 = GeniusArtistSongsPage(GeniusArtistSongs(Seq(song3), None))

  "doWork" should "request songs for an artist then scrape their lyrics" in {
    // mock request returning artist songs
    val geniusRequester = mock[GeniusRequester]
    when(geniusRequester.requestArtistSongs(artistId))
      .thenReturn(Future.successful(Seq(Future.successful(songsPg1), Future.successful(songsPg2))))

    // mock lyric scraping
    val scraper = mock[GeniusLyricsScraper]
    when(scraper.scrapeLyrics(song1.url)).thenReturn(Future.successful(song1Data.lyrics))
    when(scraper.scrapeLyrics(song2.url)).thenReturn(Future.successful(song2Data.lyrics))
    when(scraper.scrapeLyrics(song3.url)).thenReturn(Future.successful(song3Data.lyrics))

    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])

    implicit val jobFramework: JobFramework = framework(gRequest = geniusRequester, gScraper = scraper,
      dReceiver = receiver)

    ArtistLyricsJob(artistId, artist).doWork()

    // verify (with some small timeout) that our 3 songs were received
    verify(receiver, Mockito.timeout(1000).times(3)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(song1Data) shouldBe true
    capturedTracks.contains(song2Data) shouldBe true
    capturedTracks.contains(song3Data) shouldBe true
  }
}
