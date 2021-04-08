package testutils

import models.Backend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

class APIRequesterSpec extends UnitSpec {
  implicit val backend: Backend = AsyncHttpClientFutureBackend()

  def verifyPages[R](pagedResponse: Future[Seq[Future[R]]])(assertion: R => Unit): Unit =
    whenReady(pagedResponse)(_.foreach(whenReady(_)(assertion(_))))
}
