package models.api.response

import models.{AccessToken, PageableWithNext}

private object GeniusResponse {}

// TODO: Not Implemented
case class GeniusAccessToken(access_token: String) extends AccessToken {
  override def getAccessToken: String = access_token
  override def expiresIn: Int = 0
}

case class GeniusArtistSongsResponse(response: GeniusArtistSongs) extends PageableWithNext {
  override def getNextPage: Option[Int] = response.next_page
}
case class GeniusArtistSongs(songs: Seq[GeniusSong], next_page: Option[Int])
case class GeniusSong(id: Int, title: String, url: String)
