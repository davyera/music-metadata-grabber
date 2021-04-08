package service.request.genius

import testutils.UnitSpec

class GeniusLyricsScraperTest extends UnitSpec {
  val scraper = new GeniusLyricsScraper
  val url = "https://genius.com/David-bowie-space-oddity-lyrics"

  "stripSongMarkers" should "remove song markers from lyrics text" in {
    val lyrics = "[Verse1] verse [Chorus] chorus chorus [Verse 2] hello [Bridge] bridge [Outro]"
    scraper.cleanLyrics(lyrics) shouldEqual "verse chorus chorus hello bridge"
  }

  "scrapeLyrics" should "return lyrics for the given song url" in {
    scraper.scrapeLyrics(url).contains("This is Ground Control to Major Tom") should be (true)
  }
}
