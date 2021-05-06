package service.job.orchestration

import akka.http.scaladsl.model.DateTime
import com.typesafe.scalalogging.StrictLogging
import models.OrchestrationSummary
import service.data.DataPersistence
import service.job.JobEnvironment

import scala.concurrent.{ExecutionContext, Future}

/** Access to the [[JobOrchestration]] queue with [[DataPersistence]] management.
 *  Provides the orchestrations that are next in line to be run.
 */
private[orchestration] class OrchestrationLoader(implicit val jobEnvironment: JobEnvironment) extends StrictLogging {
  import scala.jdk.CollectionConverters._

  implicit private val context: ExecutionContext = jobEnvironment.context

  private[orchestration] val persistence = jobEnvironment.dataPersistence

  private val queue = new java.util.concurrent.ConcurrentHashMap[JobOrchestration[_], Unit]()

  private def put(orchestration: JobOrchestration[_]): Unit = queue.put(orchestration, ())
  private def remove(orchestration: JobOrchestration[_]): Unit = queue.remove(orchestration)
  def getAll: Seq[JobOrchestration[_]] = queue.keys().asScala.toSeq

  private val initFuture = initOrchestrationQueue()
  private[orchestration] def getInitializationFuture: Future[Unit] = initFuture

  /** Reads persisted orchestrations from the [[DataPersistence]] layer to enqueue. */
  private def initOrchestrationQueue(): Future[Unit] = {
    persistence.getOrchestrations.map { summaries: Seq[OrchestrationSummary] =>
      summaries.foreach { summary =>
        makeOrchestration(summary) match {
          case Some(orchestration) =>
            logger.info(s"Enqueuing persisted orchestration: $summary")
            put(orchestration)
          case None =>
            logger.warn(s"Could not load orchestration: $summary")
        }
      }
    }
  }

  /** Converts an [[OrchestrationSummary]] into an instance of a [[JobOrchestration]] */
  private[orchestration] def makeOrchestration(summary: OrchestrationSummary): Option[JobOrchestration[_]] = {
    val time = DateTime.fromIsoDateTimeString(summary.scheduled_for)
    val schedule = JobSchedule.make(time, Option(summary.recurrence))
    Option(summary.orchestration_type match {
      case OrchestrationType.Artist             => ArtistOrchestration.byName(summary.parameter)
      case OrchestrationType.FeaturedPlaylists  => FeaturedPlaylistsOrchestration(schedule)
      case OrchestrationType.CategoryPlaylists  => CategoryPlaylistsOrchestration(summary.parameter, schedule)
      case unknown                              =>
        logger.error(s"Invalid Orchestration Type: $unknown")
        null
    })
  }

  /** Enqueues the [[JobOrchestration]] and persists it in the [[DataPersistence]] layer */
  def enqueue(jobOrchestration: JobOrchestration[_]): Unit = {
    put(jobOrchestration)
    val summary = jobOrchestration.summarize
    logger.info(s"Persisting orchestration: $summary")
    persistence.persistOrchestration(summary)
  }

  /** Dequeues the [[JobOrchestration]] and removes it from the [[DataPersistence]] layer */
  def dequeue(jobOrchestration: JobOrchestration[_]): Unit = {
    remove(jobOrchestration)
    val summary = jobOrchestration.summarize
    logger.info(s"Un-persisting orchestration: $summary")
    persistence.removeOrchestration(summary)
  }

  /** Returns the [[JobOrchestration]] with the earliest scheduled time that is ready to be run */
  private[orchestration] def getNextInLine: Option[JobOrchestration[_]] = {
    val nextOrch = getAll.reduceLeft(nextInLine)
    if (nextOrch.schedule.isReady)
      Some(nextOrch)
    else
      None
  }

  /** Compares 2 [[JobOrchestration]] schedules and returns which one should be run first */
  private[orchestration] def nextInLine(orch1: JobOrchestration[_], orch2: JobOrchestration[_]): JobOrchestration[_] =
    if (orch1.schedule.time < orch2.schedule.time) orch1 else orch2
}