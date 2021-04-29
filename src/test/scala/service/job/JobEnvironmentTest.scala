package service.job

import testutils.UnitSpec

import scala.concurrent.Future

class JobEnvironmentTest extends UnitSpec {
  class TestJob(implicit env: JobEnvironment) extends DataJob[Unit] {
    override private[job] def work: Future[Unit] = Future.successful(())
    override private[job] val serviceName = ""
    override private[job] val jobName = ""
    override private[job] def recovery: Unit = ()
  }

  def mkJob(completed: Boolean, failed: Boolean)
           (implicit env: JobEnvironment): DataJob[_] = {
    new TestJob() {
      override def isComplete: Boolean = completed
      override def isFailed: Boolean = failed
    }
  }

  "unfinishedJobs" should "list all registered jobs that are not yet finished" in {
    implicit val env: JobEnvironment = new JobEnvironment()
    val job1_finished = mkJob(completed = true, failed = false)
    val job2_unfinished = mkJob(completed = false, failed = false)
    val job3_unfinished = mkJob(completed = false, failed = true)
    env.registerJob(job1_finished)
    env.registerJob(job2_unfinished)
    env.registerJob(job3_unfinished)
    val unfinishedJobs = env.unfinishedJobs
    unfinishedJobs.size shouldEqual 2
    unfinishedJobs.contains(job1_finished) shouldEqual false
    unfinishedJobs.contains(job2_unfinished) shouldEqual true
    unfinishedJobs.contains(job3_unfinished) shouldEqual true
  }

  "failedJobs" should "list all failed jobs whether or not they are finished" in {
    implicit val env: JobEnvironment = new JobEnvironment()
    val job1_failed = mkJob(completed = true, failed = true)
    val job2_success= mkJob(completed = true, failed = false)
    val job3_failed = mkJob(completed = false, failed = true)
    env.registerJob(job1_failed)
    env.registerJob(job2_success)
    env.registerJob(job3_failed)
    val failedJobs = env.failedJobs
    failedJobs.size shouldEqual 2
    failedJobs.contains(job1_failed) shouldEqual true
    failedJobs.contains(job2_success) shouldEqual false
    failedJobs.contains(job3_failed) shouldEqual true
  }
}
