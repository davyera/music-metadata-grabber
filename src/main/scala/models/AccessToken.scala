package models

import utils.Expirable

abstract class AccessToken extends Expirable {
  def getAccessToken: String
  def expiresIn: Int

  private val expiresOn = getExpiry(expiresIn)
  def expired: Boolean = isExpired(expiresOn)
}
