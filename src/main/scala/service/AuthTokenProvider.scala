package service

import com.typesafe.scalalogging.StrictLogging
import models.{AccessToken, Backend, Request}
import utils.Configuration

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

abstract class AuthTokenProvider[T <: AccessToken](implicit val backend: Backend,
                                                   implicit val context: ExecutionContext,
                                                   implicit val config: Configuration = Configuration)
  extends StrictLogging {

  lazy private val maxRetries = 3
  lazy private val retryWait = config.httpRequestRetryTimeMS

  lazy private val tokenRef: AtomicTokenRef[T] = {
    val initToken = requestTokenWithRetries()
    AtomicTokenRef[T](initToken, context)
  }

  protected val requestWithAuth: Request[T]

  def getAuthToken: Future[T] = getValidToken(tokenRef)

  private def getValidToken(tokenRef: AtomicTokenRef[T]): Future[T] = {
    // check if expiration has been reached
    tokenRef.isExpired.map { expired =>
      // refresh if needed
      if (expired)
        tokenRef.refreshToken(requestTokenWithRetries())
    }
    tokenRef.getToken
  }

  private def requestTokenWithRetries(): Future[T] = {
    requestWithRetries(requestTokenBasicAuth(), maxRetries, retryWait)
  }

  private def requestTokenBasicAuth(): Future[T] = {
    requestWithAuth.send().map(_.body).map {
      case Right(validTokenResponse) =>
        logger.info("Valid Auth response!")
        validTokenResponse
      case Left(error) =>
        logger.info("Got invalid Auth response.")
        throw error
    }
  }

  private def requestWithRetries(request: => Future[T], iteration: Int, waitMS: Int): Future[T] =
    Future.unit.flatMap(_ => request).recoverWith {
      case _ if iteration > 0 =>
        logger.info(s"Waiting ${waitMS}ms then retrying request... tries left: $iteration")
        Thread.sleep(waitMS)
        requestWithRetries(request, iteration - 1, waitMS)
      case error =>
        logger.info(s"Could not finish request.")
        Future.failed(error)
    }
}

private case class AtomicTokenRef[T <: AccessToken](private val token: Future[T],
                                                    private implicit val context: ExecutionContext) {

  private val atomicTokenRef: AtomicReference[Future[T]] = new AtomicReference(token)

  def getToken: Future[T] = atomicTokenRef.get()
  def isExpired: Future[Boolean] = atomicTokenRef.get().map(_.expired)
  def refreshToken(newToken: Future[T]): Unit = atomicTokenRef.set(newToken)
}