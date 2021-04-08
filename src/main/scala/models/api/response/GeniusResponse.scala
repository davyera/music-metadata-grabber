package models.api.response

import models.AccessToken

private object GeniusResponse {}

// TODO: Not Implemented
case class GeniusAccessToken(access_token: String) extends AccessToken {
  override def getAccessToken: String = access_token
  override def expiresIn: Int = 0
}
