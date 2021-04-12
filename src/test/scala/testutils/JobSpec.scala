package testutils

import models.Backend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

class JobSpec extends UnitSpec {
  implicit val backend: Backend = AsyncHttpClientFutureBackend()
}
