package models

abstract class AccessToken {
  def getAccessToken: String
  def expiresIn: Int

  private def currentTimeSeconds = System.currentTimeMillis()/1000

  private val expiresOn = currentTimeSeconds + expiresIn
  def expired: Boolean = currentTimeSeconds >= expiresOn
}
