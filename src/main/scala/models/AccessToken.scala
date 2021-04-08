package models

abstract class AccessToken {
  def getAccessToken: String
  def expiresIn: Int

  private def currentTimeSeconds = System.currentTimeMillis()/1000

  private val expiresOn = currentTimeSeconds + expiresIn
  def expired: Boolean = currentTimeSeconds >= expiresOn
}

/** GENERIC, NON-EXPIRING HARDCODED AUTHORIZATION TOKEN */
case class HardcodedToken(access_token: String) extends AccessToken {
  override def getAccessToken: String = access_token
  override def expiresIn: Int = 0 // ignored
  override def expired: Boolean = false
}
