package service.job.orchestration

import akka.http.scaladsl.model.DateTime
import service.job.orchestration.JobRecurrence._
import testutils.UnitSpec

class JobScheduleTest extends UnitSpec {
  "willRecur" should "return false if recurrence is Once" in {
    val schedule = JobSchedule(DateTime.now, Once)
    schedule.willRecur shouldEqual false
  }

  "willRecur" should "return true if recurrence is not Once" in {
    val schedule = JobSchedule(DateTime.now, Weekly)
    schedule.willRecur shouldEqual true
  }

  "getNextSchedule" should "not return a schedule when recurrence is Once" in {
    val schedule = JobSchedule(DateTime.now, Once)
    schedule.getNextSchedule shouldEqual None
  }

  "getNextSchedule" should "return a schedule for the next day when Daily" in {
    val time = DateTime.now
    val schedule = JobSchedule(time, Daily)
    schedule.getNextSchedule shouldEqual Some(JobSchedule(time + 1000*60*60*24, Daily))
  }

  "getNextSchedule" should "return a schedule for the next week when Weekly" in {
    val time = DateTime.now
    val schedule = JobSchedule(time, Weekly)
    schedule.getNextSchedule shouldEqual Some(JobSchedule(time + 1000*60*60*24*7, Weekly))
  }

  "getNextSchedule" should "return a schedule for the next month when Monthly" in {
    val time = DateTime.now
    val schedule = JobSchedule(time, Monthly)
    schedule.getNextSchedule shouldEqual Some(JobSchedule(time + 1000*60*60*24*30, Monthly))
  }

  "make" should "convert time and recurrence options into a JobSchedule" in {
    val time = DateTime.now
    JobSchedule.make(Option(time), Option(JobRecurrence.Weekly)) shouldEqual JobSchedule(time, JobRecurrence.Weekly)
  }

  "make" should "provide default values when no time or recurrence are provided" in {
    val initTime = DateTime.now
    val schedule = JobSchedule.make(None, None)
    schedule.time should be >= initTime
    schedule.recurrence shouldEqual JobRecurrence.Once
  }

}
