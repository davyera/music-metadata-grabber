package service.job.orchestration

import com.typesafe.scalalogging.StrictLogging
import models.OrchestrationSummary
import service.job.JobEnvironment
import service.job.orchestration.OrchestrationType.OrchestrationType

abstract class JobOrchestration[+T](val schedule: JobSchedule = ASAP)
                                   (implicit jobEnvironment: JobEnvironment) extends StrictLogging {

  private val subOrchestrations = new java.util.concurrent.ConcurrentHashMap[JobOrchestration[_], Unit]()

  val id: String = java.util.UUID.randomUUID.toString

  /** Main work for this orchestration */
  private[orchestration] def work: T

  /** Optional input parameter for the orchestration */
  protected val inputParameter: String = ""

  private[orchestration] val orchestrationType: OrchestrationType
  private[orchestration] def getNextRecurrence: JobOrchestration[T]
  private[orchestration] def logInfo(msg: String): Unit = logger.info(s"ORCHESTRATION:$orchestrationType: $msg")

  /** Add future sub orchestration for the [[OrchestrationMaster]] to enqueue once this job is completed. */
  private[orchestration] def addSubOrchestration(orchestration: JobOrchestration[_]): Unit =
    subOrchestrations.put(orchestration, ())

  private[orchestration] def getSubOrchestrations: Seq[JobOrchestration[_]] = {
    import scala.jdk.CollectionConverters._
    subOrchestrations.keys().asScala.toSeq
  }

  def summarize: OrchestrationSummary =
    OrchestrationSummary(id, orchestrationType, inputParameter, schedule.time.toIsoDateString(), schedule.recurrence)

  def getSummaryMsg: String = s"$orchestrationType (Recurrence: ${schedule.recurrence})"
}



