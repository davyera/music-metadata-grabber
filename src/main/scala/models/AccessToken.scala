package models

import utils.TimeTracked

abstract class AccessToken extends TimeTracked {
  def getAccessToken: String
  def expiresIn: Int

  private val expiresOn = getExpiry(expiresIn)
  def expired: Boolean = isExpired(expiresOn)
}
