package service.job

import com.typesafe.scalalogging.StrictLogging
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.SpotifyRequester

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

abstract class DataJob[T](private implicit val jobFramework: JobFramework) extends StrictLogging {

  private val MAX_JOB_TIMEOUT_MS: FiniteDuration = 2000.milliseconds

  private[job] val spotify:       SpotifyRequester    = jobFramework.spotify
  private[job] val genius:        GeniusRequester     = jobFramework.genius
  private[job] val geniusScraper: GeniusLyricsScraper = jobFramework.geniusScraper

  implicit private[job] val context: ExecutionContext = jobFramework.context

  def sendData[D](data: D): Unit = {
    jobFramework.receiver.receive(data)
  }

  def doWork(): Future[T] = {
    // TODO: pre-work stuff: start a timer? Set as pending? Register work being started?
    val workFuture = work
    // TODO: post-work stuff. finish timer? Set as completed? Successful? Failed?
    workFuture
  }

  /** Override with service.job workload -- should not be called (use [[doWork()]]) */
  private[job] def work: Future[T]
  private[job] val serviceName: String

  private[job] def logInfo(msg: String): Unit = logger.info(s"$serviceName: $msg")
  private[job] def exception(msg: String): JobException = JobException(s"$serviceName: $msg")

  private[job] def awaitPagedResults[O](pagedResults: Seq[Future[Seq[O]]]): Seq[O] = pagedResults.flatten(awaitResult)
  private[job] def awaitResult[O](future: Future[O]): O = Await.result(future, MAX_JOB_TIMEOUT_MS)

  /** Apply a function to paged results from a paged API response.
   */
  private[job] def workOnPages[P, O](pages: Seq[Future[P]])(pageWork: P => O): Seq[Future[O]] =
    pages.map { pageFuture: Future[P] =>
      pageFuture.map { page: P =>
        pageWork(page)
      }
    }
}

abstract class SpotifyJob[T](implicit jobFramework: JobFramework) extends DataJob[T] {
  override val serviceName: String = "SPOTIFY"
}

abstract class GeniusJob[T](implicit jobFramework: JobFramework) extends DataJob[T] {
  override val serviceName: String = "GENIUS"
}

case class JobException(msg: String) extends Exception(msg)