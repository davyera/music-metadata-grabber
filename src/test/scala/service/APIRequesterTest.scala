package service

import models.{Backend, Pageable}
import service.request.spotify.SpotifyAuthTokenProvider
import testutils.UnitSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

class APIRequesterTest extends UnitSpec {

  implicit val backend: Backend = mock[Backend]
  implicit val authTokenProvider: SpotifyAuthTokenProvider = mock[SpotifyAuthTokenProvider]
  class TestAPIRequester extends APIRequester

  class TestPageable(totalItems: Int) extends Pageable {
    override def getTotal: Int = totalItems
  }

  val testCounter: AtomicInteger = new AtomicInteger(0)
  before {
    testCounter.set(0)
  }

  def getPageFunction(pageable: Seq[TestPageable]): (Int, Int) => Future[TestPageable] =
    (_: Int, _: Int) => Future(pageable(testCounter.getAndIncrement()))

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
}
