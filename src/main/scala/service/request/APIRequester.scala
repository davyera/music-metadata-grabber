package service.request

import com.typesafe.scalalogging.StrictLogging
import models.{Backend, PageableWithNext, PageableWithTotal, Response}
import sttp.model.StatusCode
import utils.TimeTracked

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

private[request] case class RateLimitException(durationS: Long) extends Exception

abstract class APIRequester(val authProvider: AuthTokenProvider)
                           (implicit val backend: Backend,
                            implicit val context: ExecutionContext) extends StrictLogging with TimeTracked {

  private val REQUEST_TIMEOUT_S = 2
  private val DEFAULT_RATE_LIMIT_WAIT_S = 5

  // functionality for rate limiting: thread-safe rate limit and methods for getting, setting, and waiting for it
  private val rateLimitExpiration = new AtomicLong(0)
  private def rateLimited: Boolean = !isExpired(rateLimitExpiration.get())
  private def updateRateLimitExpiry(duration: Long): Unit = rateLimitExpiration.set(getExpiry(duration))
  private def secondsTilExpiry: Long = secondsTilExpiry(rateLimitExpiration.get())

  /** Performs a get request. If we are currently rate-limited, will wait until the limit expires before */
  private[request] def get[R](request: APIGetRequest[R]): Future[R] = {
    Future {
      // use var to break loop if request completed successfully
      var result: Option[R] = None

      // loop until we get a response (or a failure)
      while (result.isEmpty) {

        // if we are rate limited, just wait the appropriate amount of time
        if (rateLimited) Thread.sleep(secondsTilExpiry * 1000)

        try {
          // try request and await result (within surrounding future)
          result = Some(
            Await.result(doGet(request), REQUEST_TIMEOUT_S.seconds)
          )
        } catch {
          case RateLimitException(duration) =>
            logger.info(s"Hit with rate limit, holding off (${duration}sec)")
            updateRateLimitExpiry(duration)
          case e: Throwable => throw e
        }
      }
      result.get
    }
  }


  private[request] def doGet[R](request: APIGetRequest[R]): Future[R] =
    authProvider.getAuthTokenString.flatMap { token: String =>
      val requestWithAuth = request.baseRequest.auth.bearer(token)
      requestWithAuth.send().map { response: Response[R] =>
        response.code match {
          case StatusCode.Ok              => handleSuccess(response)
          case StatusCode.TooManyRequests => handleRateLimit(response)
          case _                          => throw new Exception(s"Unexpected HTTP response:\n${response.body}")
        }
      }
    }

  private def handleSuccess[R](response: Response[R]): R =
    response.body match {
      case Right(validResponse) =>  validResponse
      case Left(error) =>           throw error
    }

  private def handleRateLimit[R](response: Response[R]): Nothing = {
    val duration = response.header("Retry-After") match {
      case Some(duration) =>  duration.toLong
      case None =>            DEFAULT_RATE_LIMIT_WAIT_S
    }
    throw RateLimitException(duration)
  }

  /** Returns a Future of a sequence of Future page results.
   *  Outer future is contingent on first page finishing -- as the rest of the pages depend on this result.
   *  The sequence of futures within are the expected remaining page results.
   */
  protected[service] def queryPages[R <: PageableWithTotal](limitPerRequest: Int,
                                                            pagedFunction: (Int, Int) => Future[R])
  : Future[Seq[Future[R]]] = {
    logger.info(s"Calling paged function... limit $limitPerRequest offset 0")
    val firstResult: Future[R] = pagedFunction(limitPerRequest, 0)
    firstResult.map { firstPage: R =>
      // we only find total results after first page is returned
      val totalItems = firstPage.getTotal

      // to find the number of remaining pages, we subtract the initial page length from the total then divide by size per page
      val numPagesLeft = math.ceil((totalItems.toDouble - limitPerRequest) / limitPerRequest).toInt

      // make a new set of offsets, incremented by the limit each time
      val newOffsets: Seq[Int] = Array.iterate(limitPerRequest, numPagesLeft)(limitPerRequest.+).toSeq

      // call the paged function for each new offset
      val remainingPages: List[Future[R]] = newOffsets.map{offset =>
        logger.info(s"Calling paged function... limit $limitPerRequest offset $offset")
        pagedFunction(limitPerRequest, offset)
      }.toList

      // merge our first page (at this point much already be successful) with the newfound remaining pages
      Future.successful(firstPage) :: remainingPages
    }
  }

  /** Returns a Future of a sequence of Future page results, queried sequentially
   *  Each page needs to be queried before the next one can be queried
   *  Use when the subsequent page relies on results of current page
   */
  protected[service] def queryPagesSequential[R <: PageableWithNext](limitPerRequest: Int,
                                                                     pagedFunction: (Int, Int) => Future[R],
                                                                     page: Int = 1)
  : Future[Seq[Future[R]]] = {
    pagedFunction(limitPerRequest, page).flatMap { page: R =>
      val nextPages: Future[Seq[Future[R]]] = page.getNextPage match {
        // if "nextPage" is defined in the json, we need to recursively query for it
        case Some(nextPage) => queryPagesSequential[R](limitPerRequest, pagedFunction, nextPage)
        // if "nextPage" is null, we have reached the end of our querying
        case None => Future.successful(Nil)
      }
      // build our sequence of futures like this: appending current page to the subsequent page list
      nextPages.map(pages => Seq(Future.successful(page)) ++ pages)
    }
  }
}