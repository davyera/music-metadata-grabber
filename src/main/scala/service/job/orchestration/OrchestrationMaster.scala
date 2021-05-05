package service.job.orchestration

import akka.http.scaladsl.model.DateTime
import com.typesafe.scalalogging.StrictLogging
import models.OrchestrationSummary
import models.api.webapp.JobSummary
import service.SimpleScheduledTask
import service.data.{DataPersistence, DbPersistence}
import service.job.JobEnvironment
import service.job.orchestration.JobRecurrence.JobRecurrence
import service.job.orchestration.OrchestrationType.OrchestrationType

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
class OrchestrationMaster(private implicit val context: ExecutionContext) extends StrictLogging {
  val dataPersistence: DataPersistence = new DbPersistence()
  val jobEnvironment: JobEnvironment = new JobEnvironment(dataPersistence)

  private implicit val master: OrchestrationMaster = this

  private val queuedOrchestrations = new QueuedOrchestrations()
  private val orchestrationInProgress = new AtomicReference[Option[JobOrchestration[_]]](None)

  private[orchestration] def getQueuedOrchestrations: Seq[JobOrchestration[_]] = queuedOrchestrations.get

  // TODO: Also publish to DB
  private[orchestration] def enqueue(jobOrchestration: JobOrchestration[_]): Unit =
    queuedOrchestrations.put(jobOrchestration)

  private def makeOrchestration(orchestrationType: OrchestrationType,
                                parameter: String,
                                time: DateTime,
                                recurrence: JobRecurrence): Option[JobOrchestration[_]] = {
    val schedule: JobSchedule = JobSchedule(time, recurrence)
    Option(orchestrationType match {
      case OrchestrationType.Artist             => ArtistOrchestration.byName(parameter)
      case OrchestrationType.FeaturedPlaylists  => FeaturedPlaylistsOrchestration(schedule)
      case OrchestrationType.CategoryPlaylists  => CategoryPlaylistsOrchestration(parameter, schedule)
      case unknown                              =>
        logger.error(s"Invalid Orchestration Type: $unknown")
        null
    })
  }

  def enqueueArtistOrchestration(artistName: String): Unit =
    enqueue(ArtistOrchestration.byName(artistName))

  def enqueueFeaturedPlaylistsOrchestration(time: Option[DateTime],
                                            recurrence: Option[JobRecurrence]): Unit = {
    val schedule = makeSchedule(time, recurrence)
    enqueue(FeaturedPlaylistsOrchestration(schedule))
  }

  def enqueueCategoryPlaylistsOrchestration(categoryId: String,
                                            time: Option[DateTime],
                                            recurrence: Option[JobRecurrence]): Unit = {
    val schedule = makeSchedule(time, recurrence)
    enqueue(CategoryPlaylistsOrchestration(categoryId, schedule))
  }

  private def makeSchedule(timeOpt: Option[DateTime], recurrenceOpt: Option[JobRecurrence]): JobSchedule = {
    val time = timeOpt match {
      case Some(t)    => t
      case None       => DateTime.now
    }
    val recurrence = recurrenceOpt match {
      case Some(rec)  => rec
      case None       => JobRecurrence.Once
    }
    JobSchedule(time, recurrence)
  }

  // TODO: Also delete from DB
  private def dequeue(jobOrchestration: JobOrchestration[_]): Unit = queuedOrchestrations.remove(jobOrchestration)

  /** Every 10 seconds we will try to run a new [[JobOrchestration]] if possible */
  private[orchestration] lazy val pollTimeoutMs: Int = 10000
  SimpleScheduledTask(pollTimeoutMs, TimeUnit.MILLISECONDS, () => poll())
  private def poll(): Unit = {
    if (!isWorking && getQueuedOrchestrations.nonEmpty) {
      getNextReadyOrchestration match {
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
    dequeue(orchestration)

    val summary = orchestration.getSummaryMsg
    val futureResult = Future {
      logger.info(s"Initializing orchestration: $summary")
      orchestration.work
      logger.info(s"Orchestration complete! $summary")

      orchestrationInProgress.set(None)
      true
    }.recover { error =>
      logger.info(s"Error when running orchestration $summary:\n${error.getMessage}")
      false
    }

    // if this is a recurring orchestration, enqueue the next one now
    if (orchestration.schedule.willRecur) {
      val nextRecurrence = orchestration.getNextRecurrence
      logger.info(s"Enqueueing recurring orchestration: ${nextRecurrence.getSummaryMsg}")
      enqueue(orchestration.getNextRecurrence)
    }
    else
      logger.info(s"No recurring orchestration found for $summary")

    futureResult
  }

  /** Returns the [[JobOrchestration]] with the earliest scheduled time that is ready to be run */
  private[orchestration] def getNextReadyOrchestration: Option[JobOrchestration[_]] = {
    val nextOrch = getQueuedOrchestrations.reduceLeft(nextInLine)
    if (nextOrch.schedule.isReady)
      Some(nextOrch)
    else
      None
  }

  /** Compares 2 [[JobOrchestration]] schedules and returns which one should be run first */
  private[orchestration] def nextInLine(orch1: JobOrchestration[_], orch2: JobOrchestration[_]): JobOrchestration[_] =
    if (orch1.schedule.time < orch2.schedule.time) orch1 else orch2

  def getQueuedOrchestrationSummaries: Seq[OrchestrationSummary] = getQueuedOrchestrations.map(_.summarize)

  def getCurrentOrchestrationSummary: Option[OrchestrationSummary] =
    orchestrationInProgress.get match {
      case Some(orchestration) => Some(orchestration.summarize)
      case None => None
    }

  def getJobSummaries: Seq[JobSummary] = jobEnvironment.getJobs.map(_.summarize)

  def deleteData(): Future[Boolean] = dataPersistence.deleteData()
}

private class QueuedOrchestrations {
  import scala.jdk.CollectionConverters._

  private val queuedOrchestrations = new java.util.concurrent.ConcurrentHashMap[JobOrchestration[_], Unit]()

  def put(jobOrchestration: JobOrchestration[_]): Unit = queuedOrchestrations.put(jobOrchestration, ())
  def remove(jobOrchestration: JobOrchestration[_]): Unit = queuedOrchestrations.remove(jobOrchestration)
  def get: Seq[JobOrchestration[_]] = queuedOrchestrations.keys().asScala.toSeq
}
