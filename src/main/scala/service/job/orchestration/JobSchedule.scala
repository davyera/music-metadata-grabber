package service.job.orchestration

import akka.http.scaladsl.model.DateTime
import service.job.orchestration.JobRecurrence.JobRecurrence

object JobRecurrence extends Enumeration {
  type JobRecurrence = String
  val Once    = "once"
  val Daily   = "daily"
  val Weekly  = "weekly"
  val Monthly = "monthly"
}

case class JobSchedule(time: DateTime = DateTime.now, recurrence: JobRecurrence = JobRecurrence.Once) {
  private val dayMillis = 1000 * 60 * 60 * 24

  def isReady: Boolean = time < DateTime.now

  def willRecur: Boolean = !recurrence.equals(JobRecurrence.Once)

  def getNextSchedule: Option[JobSchedule] =
    nextTime match {
      case Some(time) => Some(JobSchedule(time, recurrence))
      case None       => None
    }

  private val nextTime: Option[DateTime] =
    recurrence.toLowerCase match {
      case JobRecurrence.Once     => None
      case JobRecurrence.Daily    => Some(time + dayMillis)
      case JobRecurrence.Weekly   => Some(time + dayMillis * 7)
      case JobRecurrence.Monthly  => Some(time + dayMillis * 30)
    }
}

object ASAP extends JobSchedule(DateTime.now, JobRecurrence.Once)