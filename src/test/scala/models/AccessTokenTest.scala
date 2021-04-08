package models

import testutils.UnitSpec

class AccessTokenTest extends UnitSpec {

  case class TestAccessToken(expiry: Int) extends AccessToken {
    override def getAccessToken: String = ""
    override def expiresIn: Int = expiry
  }

  it should "not be expired for when expiry time is long" in {
    val token = TestAccessToken(50000)
    assert(!token.expired)
  }

  it should "be expired when expiry time has elapsed" in {
    val token = TestAccessToken(0)
    Thread.sleep(100)
    assert(token.expired)
  }
}
