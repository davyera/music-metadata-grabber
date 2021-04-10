package models.api.response

import models.{AccessToken, PageableWithNext}

private object GeniusResponse {}

// TODO: Not Implemented
case class GeniusAccessToken(access_token: String) extends AccessToken {
  override def getAccessToken: String = access_token
  override def expiresIn: Int = 0
}

/** GENIUS ARTIST SEARCH */
case class GeniusSearchResponse(response: GeniusSearchHits)
case class GeniusSearchHits(hits: Seq[GeniusSearchHit], next_page: Option[Int])
case class GeniusSearchHit(result: GeniusSearchSong)
case class GeniusSearchSong(id: Int, title: String, url: String, primary_artist: GeniusSearchArtist)
case class GeniusSearchArtist(id: Int, name: String)

/** GENIUS ARTIST SONGS */
case class GeniusArtistSongsPage(response: GeniusArtistSongs) extends PageableWithNext {
  override def getNextPage: Option[Int] = response.next_page
}
case class GeniusArtistSongs(songs: Seq[GeniusSong], next_page: Option[Int])
case class GeniusSong(id: Int, title: String, url: String)
