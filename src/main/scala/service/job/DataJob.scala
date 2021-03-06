package service.job

import com.typesafe.scalalogging.StrictLogging
import models.api.webapp.JobSummary
import service.data.DataPersistence
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.SpotifyRequester

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.util.{Failure, Success}

/**
 * A unit of async work. Tracks running time, completion, success, and failure.
 * Should not do a huge amount of work -- try to aim for 1 async outbound request or so.
 */
abstract class DataJob[T](private implicit val jobEnvironment: JobEnvironment) extends StrictLogging {

  private val maxJobTimeout: FiniteDuration = 10.seconds
  private val jobCoolDownMs: Int = jobEnvironment.jobCoolDownMs
  private val pageCoolDownMs: Int = 500
  private val logStackTrace: Boolean = false

  // External data resource connections
  private[job] val spotify:       SpotifyRequester    = jobEnvironment.spotify
  private[job] val genius:        GeniusRequester     = jobEnvironment.genius
  private[job] val geniusScraper: GeniusLyricsScraper = jobEnvironment.geniusScraper

  private[job] val data:          DataPersistence     = jobEnvironment.dataPersistence

  implicit private[job] val context: ExecutionContext = jobEnvironment.context

  private val startTime = new AtomicLong(0)
  private val endTime = new AtomicLong(0)

  private val futureResult = new AtomicReference[Future[T]]()
  private val failed = new AtomicBoolean(false)
  private val failureMsg = new AtomicReference[String]("")

  /** Block for job completion and force waiting for cool-down. */
  def doWorkBlocking(): T = {
    val result = awaitResult(doWork(), recovery)
    Thread.sleep(jobCoolDownMs)
    result
  }

  def doWork(): Future[T] = {
    start()
    jobEnvironment.registerJob(this)

    futureResult.set {
      val futureWorkResult =
        if (canRecover)
          work.recover { error => handleFailure(error); recovery }
        else
          work

      // on completion, we want to record the end time and whether or not it failed (if we want to re-run later)
      futureWorkResult.onComplete {
        case Success(_)     => finish()
        case Failure(error) => finish(); handleFailure(error);
      }
      futureWorkResult
    }

    futureResult.get()
  }

  private def handleFailure(error: Throwable): Unit = {
    logError(error.getMessage)
    failed.set(true)
    failureMsg.set(error.getMessage)
    if (logStackTrace) error.printStackTrace()
  }

  /** Override with service.job workload -- should not be called (use [[doWork()]]) */
  private[job] def work: Future[T]
  /** Override with a default return if possible so we don't disrupt job chains. Throw exception if non-recoverable. */
  private[job] def recovery: T
  /** Override with value FALSE if the Job can't be recovered to a default value (ie. when searching for an ID) */
  private[job] val canRecover: Boolean = true
  private[job] val _id: String = java.util.UUID.randomUUID.toString
  private[job] val jobIdentifier: String = ""
  private[job] val serviceName: String
  private[job] val jobName: String

  private lazy val jobTag = s"$serviceName:$jobName:$jobIdentifier"

  private[job] def logInfo(msg: String): Unit = logger.info(s"$jobTag $msg")
  private[job] def logWarn(msg: String): Unit = logger.warn(s"$jobTag $msg")
  private[job] def logError(err: Throwable): Unit = { logError(err.getMessage); err.printStackTrace() }
  private[job] def logError(msg: String): Unit = logger.error(s"ERROR IN $jobTag $msg")
  private[job] def exception(msg: String): JobException = JobException(msg)

  private[job] def toTag(name: String, id: String): String = s"$name ($id)"

  // methods for simplifying / flattening / awaiting results
  private[job] def flattenChunkedResults[O](results: Seq[Future[Seq[O]]]): Future[Seq[O]] =
    Future.sequence(results).map(_.flatten)
  private[job] def awaitPagedResults[O](pagedResults: Seq[Future[Seq[O]]]): Seq[O] =
    pagedResults.flatten(awaitResult(_, Nil))
  private[job] def awaitResult[O](future: Future[O], timeoutRecovery: O = null): O =
    try
      Await.result(future, maxJobTimeout)
    catch {
      case te: TimeoutException =>
        logError("Timeout reached waiting for job. Attempting recovery...")
        te.printStackTrace()
        timeoutRecovery
      case t: Throwable => throw t
    }

  /** Apply a function to paged results from a paged API response. */
  private[job] def workOnPages[P, O](pages: Seq[Future[P]],
                                     pgCoolDownMs: Int = pageCoolDownMs)
                                    (pageWork: P => O): Seq[Future[O]] =
    pages.map { pageFuture: Future[P] =>
      pageFuture.map { page: P =>
        val result = pageWork(page)
        if (pgCoolDownMs > 0) Thread.sleep(pgCoolDownMs)
        result
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

  def summarize: JobSummary = JobSummary(_id, serviceName, jobName, isComplete, isFailed, failureMsg.get(), timeElapsed)
}

abstract class SpotifyJob[T](implicit jobEnvironment: JobEnvironment) extends DataJob[T] {
  override val serviceName: String = "SPOTIFY"
}

abstract class GeniusJob[T](implicit jobEnvironment: JobEnvironment) extends DataJob[T] {
  override val serviceName: String = "GENIUS"
}

abstract class FinalizationJob[T](implicit jobEnvironment: JobEnvironment) extends DataJob[T] {
  override val serviceName: String = "FINALIZATION"
}

case class JobException(msg: String) extends Exception(msg)