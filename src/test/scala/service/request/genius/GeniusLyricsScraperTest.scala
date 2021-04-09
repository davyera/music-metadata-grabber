package service.request.genius

import models.api.response.GeniusSong
import testutils.UnitSpec

class GeniusLyricsScraperTest extends UnitSpec {
  private val scraper = new GeniusLyricsScraper
  private val song1 = GeniusSong(1, "space oddity", "https://genius.com/David-bowie-space-oddity-lyrics")
  private val lyrics1 = "This is Ground Control to Major Tom"
  private val song2 = GeniusSong(2, "come together", "https://genius.com/The-beatles-come-together-lyrics")
  private val lyrics2 = "Got to be good-lookin' 'cause he's so hard to see"

  "stripSongMarkers" should "remove song markers from lyrics text" in {
    val lyrics = "[Verse1] verse [Chorus] chorus chorus [Verse 2] hello [Bridge] bridge [Outro]"
    scraper.cleanLyrics(lyrics) shouldEqual "verse chorus chorus hello bridge"
  }

  "scrapeLyrics" should "corresponding lyrics futures for each song" in {
    val songs = Seq(song1, song2)
    val results = scraper.scrapeLyrics(songs)
    whenReady(results.head._2)(_.contains(lyrics1) should be (true))
    whenReady(results(1)._2)(_.contains(lyrics2) should be (true))
  }

  "scrapeLyrics" should "return lyrics for the given song url" in {
    scraper.scrapeLyrics(song1.url).contains(lyrics1) should be (true)
  }
}
