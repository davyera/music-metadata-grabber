package service

import com.typesafe.scalalogging.StrictLogging
import models.{AccessToken, Backend, Pageable}

import scala.concurrent.{ExecutionContext, Future}

abstract class APIRequester(implicit val authProvider: AuthTokenProvider,
                            implicit val backend: Backend,
                            implicit val context: ExecutionContext) extends StrictLogging {

  protected def get[R](request: APIGetRequest[R]): Future[R] =
    authProvider.getAuthTokenString.flatMap { token: String =>
      val requestWithAuth = request.baseRequest.auth.bearer(token)
      val response = requestWithAuth.send()
      response.map(_.body).map {
        case Right(validResponse) => validResponse
        case Left(error) => throw error
      }
    }

  /** Returns a Future of a sequence of Future page results.
   *  Outer future is contingent on first page finishing -- as the rest of the pages depend on this result.
   *  The sequence of futures within are the expected remaining page results.
   */
  protected[service] def queryPages[R <: Pageable](limitPerRequest: Int, pagedFunction: (Int, Int) => Future[R]): Future[Seq[Future[R]]] = {
    logger.info(s"Calling paged function... limit $limitPerRequest offset 0")
    val firstResult: Future[R] = pagedFunction(limitPerRequest, 0)
    firstResult.map { firstPage: R =>
      // we only find total results after first page is returned
      val totalItems = firstPage.getTotal

      // to find the number of remaining pages, we subtract the initial page length from the total then divide by size per page
      val numPagesLeft = math.ceil((totalItems.toDouble - limitPerRequest) / limitPerRequest).toInt

      // make a new set of offsets, incremented by the limit each time
      val newOffsets: Seq[Int] = Array.iterate(limitPerRequest, numPagesLeft)(limitPerRequest.+)

      // call the paged function for each new offset
      val remainingPages: List[Future[R]] = newOffsets.map{offset =>
        logger.info(s"Calling paged function... limit $limitPerRequest offset $offset")
        pagedFunction(limitPerRequest, offset)
      }.toList

      // merge our first page (at this point much already be successful) with the newfound remaining pages
      Future.successful(firstPage) :: remainingPages
    }
  }
}
