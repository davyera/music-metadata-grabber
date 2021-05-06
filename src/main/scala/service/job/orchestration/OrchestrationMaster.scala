package service.job.orchestration

import akka.http.scaladsl.model.DateTime
import com.typesafe.scalalogging.StrictLogging
import models.OrchestrationSummary
import models.api.webapp.JobSummary
import service.SimpleScheduledTask
import service.data.{DataPersistence, DbPersistence}
import service.job.JobEnvironment
import service.job.orchestration.JobRecurrence.JobRecurrence

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

object OrchestrationType {
  type OrchestrationType = String
  val Artist            = "ARTIST"
  val FeaturedPlaylists = "FEATURED_PLAYLISTS"
  val CategoryPlaylists = "CATEGORY_PLAYLISTS"
}

/**
 *  Master controls for [[JobOrchestration]] work.
 *  Ensures only one [[JobOrchestration]] will be run at a time, selects the next one based on its [[JobSchedule]],
 *  and enqueues its next iteration if it can recur.
 */
class OrchestrationMaster()(private implicit val context: ExecutionContext) extends StrictLogging {

  val dataPersistence: DataPersistence = new DbPersistence()

  implicit lazy val jobEnvironment: JobEnvironment = new JobEnvironment(dataPersistence)

  private val orchestrationLoader = new OrchestrationLoader()

  private val orchestrationInProgress = new AtomicReference[Option[JobOrchestration[_]]](None)

  private[orchestration] def getQueuedOrchestrations: Seq[JobOrchestration[_]] =
    orchestrationLoader.getAll

  private[orchestration] def enqueue(orchestration: JobOrchestration[_]): Unit =
    orchestrationLoader.enqueue(orchestration)

  /** Enqueues an [[ArtistOrchestration]] to be run with given artist name. */
  def enqueueArtistOrchestration(artistName: String): Unit = enqueue(ArtistOrchestration.byName(artistName))

  /** Enqueues a [[FeaturedPlaylistsOrchestration]] to be run at given time and recurrence. */
  def enqueueFeaturedPlaylistsOrchestration(time: Option[DateTime],
                                            recurrence: Option[JobRecurrence]): Unit = {
    val schedule = JobSchedule.make(time, recurrence)
    enqueue(FeaturedPlaylistsOrchestration(schedule))
  }

  /** Enqueues a [[CategoryPlaylistsOrchestration]] to be run at given time and recurrence. */
  def enqueueCategoryPlaylistsOrchestration(categoryId: String,
                                            time: Option[DateTime],
                                            recurrence: Option[JobRecurrence]): Unit = {
    val schedule = JobSchedule.make(time, recurrence)
    enqueue(CategoryPlaylistsOrchestration(categoryId, schedule))
  }

  /** Every 10 seconds we will try to run a new [[JobOrchestration]] if possible */
  private[orchestration] lazy val pollTimeoutMs: Int = 10000
  SimpleScheduledTask(pollTimeoutMs, TimeUnit.MILLISECONDS, () => poll())
  private def poll(): Unit = {
    if (!isWorking && getQueuedOrchestrations.nonEmpty) {
      orchestrationLoader.getNextInLine match {
        case Some(orchestration) => handleOrchestration(orchestration)
        case _ => // do nothing
      }
    }
  }

  private[orchestration] def isWorking: Boolean = orchestrationInProgress.get().isDefined

  /**
   *  Starts the [[JobOrchestration]] work and dequeues it from the queue.
   *  Will set [[orchestrationInProgress]], preventing any other orchestrations from being run,
   *  then un-set it once complete.
   *  If the [[JobOrchestration]] can recur, will enqueue its next iteration.
   */
  private def handleOrchestration(orchestration: JobOrchestration[_]): Future[Boolean] = {
    orchestrationInProgress.set(Some(orchestration))
    orchestrationLoader.dequeue(orchestration)

    val summary = orchestration.getSummaryMsg
    val futureResult = Future {
      logger.info(s"Initializing orchestration: $summary")
      orchestration.work
      logger.info(s"Orchestration complete! $summary")

      // enqueue any Sub-Orchestrations, if any were created
      val subOrchestrations = orchestration.getSubOrchestrations
      if (subOrchestrations.nonEmpty)
        subOrchestrations.foreach { sub =>
          logger.info(s"Enqueueing sub-orchestration: ${sub.getSummaryMsg}")
          orchestrationLoader.enqueue(sub)
        }

      orchestrationInProgress.set(None)
      true
    }.recover { error =>
      orchestrationInProgress.set(None)
      logger.error(s"Error when running orchestration $summary: ${error.getMessage}")
      false
    }

    // if this is a recurring orchestration, enqueue the next one now
    if (orchestration.schedule.willRecur) {
      val nextRecurrence = orchestration.getNextRecurrence
      logger.info(s"Enqueueing recurring orchestration: ${nextRecurrence.getSummaryMsg}")
      orchestrationLoader.enqueue(orchestration.getNextRecurrence)
    }
    else
      logger.info(s"No recurring orchestration found for $summary")

    futureResult
  }

  /** Returns all currently queued [[JobOrchestration]]s  */
  def getQueuedOrchestrationSummaries: Seq[OrchestrationSummary] = getQueuedOrchestrations.map(_.summarize)

  /** Returns the summary of the currently-running [[JobOrchestration]], or None if there isn't one running. */
  def getCurrentOrchestrationSummary: Option[OrchestrationSummary] =
    orchestrationInProgress.get match {
      case Some(orchestration) => Some(orchestration.summarize)
      case None => None
    }

  /** Provides [[JobSummary]] objects for all currently loaded jobs. */
  def getJobSummaries: Seq[JobSummary] = jobEnvironment.getJobs.map(_.summarize)

  /** Deletes all data (metadata & persisted orchestrations) from the [[DataPersistence]] layer. */
  def deleteData(): Future[Boolean] = dataPersistence.deleteData()
}