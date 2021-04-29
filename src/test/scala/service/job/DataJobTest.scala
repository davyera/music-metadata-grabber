package service.job

import org.mockito.Mockito
import org.mockito.Mockito._
import testutils.UnitSpec

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.Future

class DataJobTest extends UnitSpec {

  def mkJob[T](workFn: => T, recover: Boolean = false): DataJob[T] = {
    implicit val env: JobEnvironment = mock[JobEnvironment]
    when(env.context).thenReturn(context)

    val job = new DataJob[T] {
      override def work: Future[T] = Future {
        while (!completeJob.get()) {
          Thread.sleep(10)
        }
        workFn
      }
      override val serviceName: String = "TEST"
      override val jobName: String = "TEST"
      override val canRecover: Boolean = recover
      override def recovery: T = throw new Exception()
    }

    Mockito.doNothing().when(env).registerJob(job)
    job
  }

  val completeJob: AtomicBoolean = new AtomicBoolean(false)
  before {
    completeJob.set(false)
  }

  "doWork" should "set endTime when complete" in {
    val job = mkJob[Unit] {}

    val futureResult = job.doWork()
    job.isComplete shouldEqual false

    completeJob.set(true) // should force "job" to complete

    whenReady(futureResult) { _ =>
      Thread.sleep(10)
      job.isComplete shouldEqual true
    }
  }

  "doWork" should "register failure when exception is thrown" in {
    val job = mkJob[Unit] { throw new Exception("testError") }

    val futureResult = job.doWork()
    job.isFailed shouldEqual false

    completeJob.set(true)

    whenReady(futureResult.failed) { error =>
      error shouldBe a [Exception]
      error.asInstanceOf[Exception].getMessage shouldEqual "testError"
      job.isFailed shouldEqual true
    }
  }

  "doWork" should "report correct time elapsed" in {
    val job = mkJob[Unit] {}

    // time should be 0 before job starts
    job.timeElapsed shouldEqual 0

    val futureResult = job.doWork()
    Thread.sleep(10)

    // time should start counting since we have started the job
    val time1 = job.timeElapsed
    time1 > 0 shouldEqual true

    completeJob.set(true)

    whenReady(futureResult) { _ =>
      Thread.sleep(10)

      // time should stop counting now that work is done
      val time2 = job.timeElapsed
      time2 > time1 shouldEqual true

      Thread.sleep(10)

      job.timeElapsed shouldEqual time2
    }
  }
}
