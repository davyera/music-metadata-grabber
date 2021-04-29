package service.request

import models.api.resources.spotify.SpotifyArtist
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

  case class TestPg(totalItems: Int = 0, nextPage: Option[Int] = None)
    extends PageableWithTotal with PageableWithNext {

    override def getTotal: Int = totalItems
    override def getNextPage: Option[Int] = nextPage
  }

  val testCounter: AtomicInteger = new AtomicInteger(0)
  before {
    testCounter.set(0)
  }

  def getPageFunction(pageable: Seq[TestPg]): (Int, Int) => Future[TestPg] =
    (_: Int, _: Int) => Future(pageable(testCounter.getAndIncrement()))

  "get" should "handle a rate-limiting response" in {
    val request = SpotifyArtistRequest("a1")
    val mockBackend: Backend = AsyncHttpClientFutureBackend.stub
      .whenRequestMatches(_.uri.path.contains("a1"))
      // first return a rate-limit response, then a successful response
      .thenRespondCyclicResponses(
        Response("whoops", StatusCode.TooManyRequests, "", Seq(Header("Retry-After","1")), Nil),
        Response("""{"id":"a1","name":"n","genres":[], "popularity":10}""", StatusCode.Ok))

    val logVerifier = getLogVerifier[TestAPIRequester]

    val requester = new TestAPIRequester(mockBackend)
    whenReady(requester.get(request)) { artist: SpotifyArtist =>
      artist.id shouldEqual "a1"
      artist.name shouldEqual "n"
      artist.popularity shouldEqual 10
      logVerifier.assertLogged("Hit with rate limit, holding off (1sec)")
    }
  }

  "queryPages" should "only create one page result future when total items is less than limit" in {
    testPages(5, Seq(TestPg(3)))
  }

  "queryPages" should "create 3 page result futures with a limit of 5 and total count of 12" in {
    testPages(5, Seq(TestPg(12), TestPg(), TestPg()))
  }

  "queryPages" should "create 2 page result futures with a limit of 5 and total count of 10" in {
    testPages(5, Seq(TestPg(10), TestPg()))
  }

  "queryPages" should "create 1 page result future with a limit of limit of 5 and total count of 1" in {
    testPages(5, Seq(TestPg(1)))
  }

  "queryPages" should "create 1 page result future with a limit of limit of 5 and total count of 0" in {
    testPages(5, Seq(TestPg()))
  }

  private def testPages(limit: Int, pgResults: Seq[TestPg]): Unit = {
    val pageFn = getPageFunction(pgResults)
    val request = new TestAPIRequester()
    val result = request.queryPages(limit, pageFn).flatMap(Future.sequence(_))
    whenReady(result) { pages => pages.toSet shouldEqual pgResults.toSet }
  }

  "queryPagesSequential" should "create one page when nextPage is null" in {
    testPagesSequential(Seq(TestPg(nextPage = None)))
  }

  "queryPagesSequential" should "create two page when nextPage is 1 then None" in {
    testPagesSequential(Seq(TestPg(nextPage = Some(1)), TestPg(nextPage = None)))
  }

  "queryPagesSequential" should "create multiple pages sequentially until nextPage is null" in {
    testPagesSequential(Seq(
      TestPg(nextPage = Some(1)),
      TestPg(nextPage = Some(2)),
      TestPg(nextPage = Some(3)),
      TestPg(nextPage = None)))
  }

  private def testPagesSequential(pgResults: Seq[TestPg]): Unit = {
    val pageFn = getPageFunction(pgResults)
    val request = new TestAPIRequester()
    val result = request.queryPagesSequential(1, pageFn).flatMap(Future.sequence(_))
    whenReady(result) { pages => pages shouldEqual pgResults }
  }
}
