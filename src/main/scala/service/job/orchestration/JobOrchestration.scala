package service.job.orchestration

import com.typesafe.scalalogging.StrictLogging
import models.OrchestrationSummary
import service.job.JobEnvironment
import service.job.orchestration.OrchestrationType.OrchestrationType


abstract class JobOrchestration[+T](val schedule: JobSchedule = ASAP)
                                   (implicit master: OrchestrationMaster) extends StrictLogging {
  implicit private[orchestration] val jobEnvironment: JobEnvironment = master.jobEnvironment
  val id: String = java.util.UUID.randomUUID.toString
  private[orchestration] def work: T
  protected val inputParameter: String = ""
  private[orchestration] val orchestrationType: OrchestrationType
  private[orchestration] def getNextRecurrence: JobOrchestration[T]
  private[orchestration] def logInfo(msg: String): Unit = logger.info(s"ORCHESTRATION:$orchestrationType: $msg")

  def summarize: OrchestrationSummary =
    OrchestrationSummary(id, orchestrationType, inputParameter, schedule.time.toIsoDateString(), schedule.recurrence)

  def getSummaryMsg: String = s"$orchestrationType (ID: $id; Recurrence: ${schedule.recurrence}) "
}



