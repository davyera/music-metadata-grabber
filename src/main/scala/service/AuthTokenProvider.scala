package service

import com.typesafe.scalalogging.StrictLogging
import models.{AccessToken, Backend, Request}
import utils.Configuration

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

abstract class AuthTokenProvider(implicit val backend: Backend,
                                 implicit val context: ExecutionContext,
                                 implicit val config: Configuration = Configuration)
  extends StrictLogging {

  lazy private val maxRetries = 3
  lazy private val retryWait = config.httpRequestRetryTimeMS

  lazy private val tokenRef: AtomicTokenRef[AccessToken] = {
    val initToken = requestTokenWithRetries()
    AtomicTokenRef[AccessToken](initToken, context)
  }

  /** @return Valid, refreshed (if needed) Bearer Auth token string for requests
   */
  def getAuthTokenString: Future[String] = getAuthToken.map(_.getAccessToken)

  protected type T <: AccessToken
  protected def requestWithAuth: Request[T]

  private def getAuthToken: Future[AccessToken] = getValidToken(tokenRef)

  private def getValidToken(tokenRef: AtomicTokenRef[AccessToken]): Future[AccessToken] = {
    // check if expiration has been reached
    tokenRef.isExpired.map { expired =>
      // refresh if needed
      if (expired)
        tokenRef.refreshToken(requestTokenWithRetries())
    }
    tokenRef.getToken
  }

  private def requestTokenWithRetries(): Future[AccessToken] = {
    requestWithRetries(requestTokenBasicAuth(), maxRetries, retryWait)
  }

  private def requestTokenBasicAuth(): Future[AccessToken] = {
    requestWithAuth.send().map(_.body).map {
      case Right(validTokenResponse) =>
        logger.info("Valid Auth response!")
        validTokenResponse
      case Left(error) =>
        logger.info("Got invalid Auth response.")
        throw error
    }
  }

  private def requestWithRetries(request: => Future[AccessToken], iteration: Int, waitMS: Int): Future[AccessToken] =
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

private case class AtomicTokenRef[T <: AccessToken](private val token: Future[AccessToken],
                                                    private implicit val context: ExecutionContext) {

  private val atomicTokenRef: AtomicReference[Future[AccessToken]] = new AtomicReference(token)

  def getToken: Future[AccessToken] = atomicTokenRef.get()
  def isExpired: Future[Boolean] = atomicTokenRef.get().map(_.expired)
  def refreshToken(newToken: Future[AccessToken]): Unit = atomicTokenRef.set(newToken)
}