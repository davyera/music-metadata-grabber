package service.request.genius

import models.api.resources.genius.GeniusSong
import testutils.UnitSpec

class GeniusLyricsScraperTest extends UnitSpec {
  private val scraper = new GeniusLyricsScraper
  private val song1 = GeniusSong(1, "space oddity", "https://genius.com/David-bowie-space-oddity-lyrics")
  private val lyrics1 = "This is Ground Control to Major Tom"

  "stripSongMarkers" should "remove song markers from lyrics text" in {
    val lyrics = "[Verse1] verse [Chorus] chorus chorus [Verse 2] hello [Bridge] bridge [Outro]"
    scraper.cleanLyrics(lyrics) shouldEqual "verse chorus chorus hello bridge"
  }

  "scrapeLyrics" should "return lyrics for the given song url" in {
    whenReady(scraper.scrapeLyrics(song1.url)){ lyrics =>
      lyrics.contains(lyrics1) should be (true)
    }
  }
}
