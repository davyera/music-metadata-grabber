package service.job

import com.typesafe.scalalogging.StrictLogging
import service.data.DataReceiver
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.SpotifyRequester

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

abstract class DataJob[T](private implicit val jobEnvironment: JobEnvironment) extends StrictLogging {

  private val MAX_JOB_TIMEOUT_MS: FiniteDuration = 2000.milliseconds

  private[job] val spotify:       SpotifyRequester    = jobEnvironment.spotify
  private[job] val genius:        GeniusRequester     = jobEnvironment.genius
  private[job] val geniusScraper: GeniusLyricsScraper = jobEnvironment.geniusScraper
  private[job] val receiver:      DataReceiver        = jobEnvironment.receiver

  implicit private[job] val context: ExecutionContext = jobEnvironment.context

  private val startTime = new AtomicLong(0)
  private val endTime = new AtomicLong(0)

  private val futureResult = new AtomicReference[Future[T]]()
  private val failed = new AtomicBoolean(false)

  def doWork(): Future[T] = {
    start()
    jobEnvironment.registerJob(this)

    futureResult.set {
      val futureWorkResult = work

      // on completion, we want to record the end time and whether or not it failed (if we want to re-run later)
      futureWorkResult.onComplete {
        case Success(_)     => finish()
        case Failure(error) => finish()
          logError(error.getMessage)
          failed.set(true)
      }
      futureWorkResult
    }

    futureResult.get()
  }

  /** Override with service.job workload -- should not be called (use [[doWork()]]) */
  private[job] def work: Future[T]
  private[job] val serviceName: String
  private[job] val jobName: String

  private lazy val jobTag = s"$serviceName:$jobName"

  private[job] def logInfo(msg: String): Unit = logger.info(s"$jobTag: $msg")
  private[job] def logWarn(msg: String): Unit = logger.warn(s"$jobTag: $msg")
  private[job] def logError(msg: String): Unit = logger.error(s"ERROR IN $jobTag: $msg")
  private[job] def exception(msg: String): JobException = JobException(msg)

  private[job] def toTag(name: String, id: String): String = s"$name ($id)"

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

  private def start(): Unit = startTime.set(System.currentTimeMillis())
  private def finish(): Unit = endTime.set(System.currentTimeMillis())

  private[job] def isComplete: Boolean = endTime.get() > 0
  private[job] def isFailed: Boolean = failed.get()
  private[job] def timeElapsed: Long = {
    val start: Long = startTime.get()
    // if complete, time elapsed should be (end time - start time)
    if (isComplete) endTime.get() - start
    else
      // if we have started but not completed, return current running time of job
      if (start > 0) System.currentTimeMillis() - start
      // otherwise, job has not started, so just return 0
      else 0
  }
}

abstract class SpotifyJob[T](implicit jobEnvironment: JobEnvironment) extends DataJob[T] {
  override val serviceName: String = "SPOTIFY"
}

abstract class GeniusJob[T](implicit jobEnvironment: JobEnvironment) extends DataJob[T] {
  override val serviceName: String = "GENIUS"
}

case class JobException(msg: String) extends Exception(msg)