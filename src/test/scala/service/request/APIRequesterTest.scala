package service.request

import models.api.response.SpotifyArtist
import models.{Backend, PageableWithNext, PageableWithTotal}
import org.mockito.Mockito._
import service.request.spotify.{SpotifyArtistRequest, SpotifyAuthTokenProvider}
import sttp.client.Response
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.{Header, StatusCode}
import testutils.UnitSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

class APIRequesterTest extends UnitSpec {

  implicit val backend: Backend = mock[Backend]
  implicit val authTokenProvider: SpotifyAuthTokenProvider = mock[SpotifyAuthTokenProvider]
  when(authTokenProvider.getAuthTokenString).thenReturn(Future.successful(""))
  class TestAPIRequester(b: Backend = backend) extends APIRequester(authTokenProvider)(backend = b, context)

  class TestPageable(totalItems: Int = 0, nextPage: Option[Int] = None)
    extends PageableWithTotal with PageableWithNext {

    override def getTotal: Int = totalItems
    override def getNextPage: Option[Int] = nextPage
  }

  val testCounter: AtomicInteger = new AtomicInteger(0)
  before {
    testCounter.set(0)
  }

  def getPageFunction(pageable: Seq[TestPageable]): (Int, Int) => Future[TestPageable] =
    (_: Int, _: Int) => Future(pageable(testCounter.getAndIncrement()))

  "get" should "handle a rate-limiting response" in {
    val request = SpotifyArtistRequest("a1")
    val mockBackend: Backend = AsyncHttpClientFutureBackend.stub
      .whenRequestMatches(_.uri.path.contains("a1"))
      // first return a rate-limit response, then a successful response
      .thenRespondCyclicResponses(
        Response("whoops", StatusCode.TooManyRequests, "", Seq(Header("Retry-After","1")), Nil),
        Response("""{"id":"a1","name":"n","genres":[], "popularity":10}""", StatusCode.Ok))

    val logVerifier = getLogVerifier[TestAPIRequester](classOf[TestAPIRequester])

    val requester = new TestAPIRequester(mockBackend)
    whenReady(requester.get(request)) { artist: SpotifyArtist =>
      artist.id shouldEqual "a1"
      artist.name shouldEqual "n"
      artist.popularity shouldEqual 10
      logVerifier.assertLogged("Hit with rate limit, holding off (1sec)")
    }
  }

  "queryPages" should "only create one page result future when total items is less than limit" in {
    val pageLimit = 5
    val pageResult = new TestPageable(3) // less than 5, no extra page results should be returned
    val pageFunction = getPageFunction(Seq(pageResult))
    val requester = new TestAPIRequester()
    whenReady(requester.queryPages(pageLimit, pageFunction)) { results: Seq[Future[TestPageable]] =>
      results.size shouldEqual 1
      whenReady(results.head)(_ shouldEqual pageResult)
    }
  }

  "queryPages" should "create 3 page result futures with a limit of limit of 5 and total count of 12" in {
    val pageLimit = 5
    val pageResults = Seq(new TestPageable(12), new TestPageable(0), new TestPageable(0) )
    val pageFunction = getPageFunction(pageResults)
    val request = new TestAPIRequester()
    whenReady(request.queryPages(pageLimit, pageFunction)) { results: Seq[Future[TestPageable]] =>
      results.size shouldEqual 3
      var resultCounter: Int = 0
      results.foreach { resultFuture =>
        whenReady(resultFuture)(_ shouldEqual pageResults(resultCounter))
        resultCounter = resultCounter + 1
      }
    }
  }

  "queryPages" should "create 2 page result futures with a limit of limit of 5 and total count of 10" in {
    val pageLimit = 5
    val pageResults = Seq(new TestPageable(10), new TestPageable(0))
    val pageFunction = getPageFunction(pageResults)
    val request = new TestAPIRequester()
    whenReady(request.queryPages(pageLimit, pageFunction)) { results: Seq[Future[TestPageable]] =>
      results.size shouldEqual 2
      var resultCounter: Int = 0
      results.foreach { resultFuture =>
        whenReady(resultFuture)(_ shouldEqual pageResults(resultCounter))
        resultCounter = resultCounter + 1
      }
    }
  }

  "queryPages" should "create 1 page result future with a limit of limit of 5 and total count of 1" in {
    val pageLimit = 5
    val pageResults = Seq(new TestPageable(1))
    val pageFunction = getPageFunction(pageResults)
    val request = new TestAPIRequester()
    whenReady(request.queryPages(pageLimit, pageFunction)) { results: Seq[Future[TestPageable]] =>
      results.size shouldEqual 1
      whenReady(results.head)(_ shouldEqual pageResults.head)
    }
  }

  "queryPages" should "create 1 page result future with a limit of limit of 5 and total count of 0" in {
    val pageLimit = 5
    val pageResults = Seq(new TestPageable(0))
    val pageFunction = getPageFunction(pageResults)
    val request = new TestAPIRequester()
    whenReady(request.queryPages(pageLimit, pageFunction)) { results: Seq[Future[TestPageable]] =>
      results.size shouldEqual 1
      whenReady(results.head)(_ shouldEqual pageResults.head)
    }
  }

  private def pg(pg: Option[Int]) = new TestPageable(nextPage = pg)

  "queryPagesSequential" should "create one page when nextPage is null" in {
    val request = new TestAPIRequester
    val pageResults = Seq(pg(None))
    val pageFunction = getPageFunction(pageResults)
    whenReady(request.queryPagesSequential(1, pageFunction)) { results: Seq[Future[TestPageable]] =>
      results.size shouldEqual 1
      whenReady(results.head)(_ shouldEqual pageResults.head)
    }
  }

  "queryPagesSequential" should "create two page when nextPage is 1 then None" in {
    val request = new TestAPIRequester
    val pageResults = Seq(pg(Some(1)), pg(None))
    val pageFunction = getPageFunction(pageResults)
    whenReady(request.queryPagesSequential(1, pageFunction)) { results: Seq[Future[TestPageable]] =>
      results.size shouldEqual 2
      whenReady(results.head)(_ shouldEqual pageResults.head)
    }
  }

  "queryPagesSequential" should "create multiple pages sequentially until nextPage is null" in {
    val request = new TestAPIRequester
    val pageResults = Seq(pg(Some(1)), pg(Some(2)), pg(Some(3)), pg(None))
    val pageFunction = getPageFunction(pageResults)
    whenReady(request.queryPagesSequential(1, pageFunction)) { results: Seq[Future[TestPageable]] =>
      results.size shouldEqual 4
    }
  }
}
