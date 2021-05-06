package service.job.orchestration

import akka.http.scaladsl.model.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import service.data.DataPersistence
import service.job.JobEnvironment

import scala.concurrent.Future

class OrchestrationMasterTest extends OrchestrationSpec {

  private val data = {
    val d = mock[DataPersistence]
    when(d.getOrchestrations).thenReturn(Future(Nil))
    when(d.persistOrchestration(any())).thenReturn(Future(true))
    d
  }
  private def jobEnv = env(data)

  private class TestOrchestrationMaster extends OrchestrationMaster() {
    override private[orchestration] lazy val pollTimeoutMs = 10
    override implicit lazy val jobEnvironment: JobEnvironment = jobEnv
  }
  def getMaster: OrchestrationMaster = new TestOrchestrationMaster

  def waitForPoll(): Unit = Thread.sleep(20)

  "enqueueArtistOrchestration" should "add an ArtistOrchestration to the OrchestrationLoader" in {
    val master = getMaster
    master.enqueueArtistOrchestration("xxx")
    master.getQueuedOrchestrations.toSet shouldEqual
      Set(ArtistOrchestration.byName("xxx")(master.jobEnvironment))
  }

  "enqueueFeaturedPlaylistsOrchestration" should "add a FeaturedPlaylistsOrchestration to the OrchestrationLoader" in {
    val master = getMaster
    val time = DateTime.now
    master.enqueueFeaturedPlaylistsOrchestration(Some(time), Some(JobRecurrence.Weekly))
    master.getQueuedOrchestrations.toSet shouldEqual
      Set(FeaturedPlaylistsOrchestration(JobSchedule(time, JobRecurrence.Weekly))(master.jobEnvironment))
  }

  "enqueueCategoryPlaylistsOrchestration" should "add a CategoryPlaylistsOrchestration to the OrchestrationLoader" in {
    val master = getMaster
    val time = DateTime.now
    master.enqueueCategoryPlaylistsOrchestration("cat1", Some(time), Some(JobRecurrence.Monthly))
    master.getQueuedOrchestrations.toSet shouldEqual
      Set(CategoryPlaylistsOrchestration("cat1", JobSchedule(time, JobRecurrence.Monthly))(master.jobEnvironment))
  }

  "getCurrentOrchestrationSummary" should "return summary of the currently running orchestration" in {
    val master = getMaster
    val orc = TestOrc(JobSchedule(DateTime.now - 999, JobRecurrence.Once))

    master.getCurrentOrchestrationSummary shouldEqual None

    master.enqueue(orc)
    waitForPoll()

    master.getCurrentOrchestrationSummary shouldEqual Some(orc.summarize)
  }

  "isWorking" should "report when an orchestration is working and when it is not" in {
    val master = getMaster

    val futureOrc = TestOrc(JobSchedule(DateTime.now + 99999, JobRecurrence.Once))
    master.enqueue(futureOrc)
    waitForPoll()

    master.isWorking shouldEqual false

    val pastOrc = TestOrc(JobSchedule(DateTime.now - 9999, JobRecurrence.Once))
    master.enqueue(pastOrc)
    waitForPoll()

    master.isWorking shouldEqual true

    pastOrc.finish() // finishing should remove this orchestration from the queue
    waitForPoll()

    master.isWorking shouldEqual false
  }

  "handleOrchestration" should "enqueue a recurring orchestration once the first one completes" in {
    val master = getMaster

    val time = DateTime.now - 9999
    val orc = TestOrc(JobSchedule(time, JobRecurrence.Daily))

    master.enqueue(orc)
    waitForPoll()

    orc.finish()
    waitForPoll()

    // at this point, first orc should have been removed from the queue and replaced with a new one for tomorrow
    val newTime = time+1000*60*60*24
    val nextOrc = TestOrc(JobSchedule(newTime, JobRecurrence.Daily))
    master.getQueuedOrchestrations shouldEqual Seq(nextOrc)
    master.isWorking shouldEqual false
  }

  "handleOrchestration" should "log when an orchestration throws an error" in {
    val master = getMaster
    val logVerifier = getLogVerifier[TestOrchestrationMaster]

    val time = DateTime.now - 9999
    val orc = TestOrc(JobSchedule(time, JobRecurrence.Once))

    master.enqueue(orc)
    waitForPoll()

    orc.throwError("oops")
    waitForPoll()

    master.getQueuedOrchestrations shouldEqual Nil
    master.isWorking shouldEqual false
    logVerifier.assertLogged("Error when running orchestration TEST (Recurrence: once): oops")
  }
}
