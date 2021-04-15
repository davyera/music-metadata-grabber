package utils

trait TimeTracked {
  private def currentTimeSeconds = System.currentTimeMillis()/1000
  def getExpiry(durationS: Long): Long = currentTimeSeconds + durationS
  def isExpired(expiryS: Long): Boolean = currentTimeSeconds >= expiryS
  def secondsTilExpiry(expiryS: Long): Long = expiryS - currentTimeSeconds
}
