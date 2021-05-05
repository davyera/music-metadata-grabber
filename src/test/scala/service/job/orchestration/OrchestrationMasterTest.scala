package service.job.orchestration

import akka.http.scaladsl.model.DateTime
import testutils.UnitSpec

import java.util.concurrent.atomic.AtomicBoolean

class OrchestrationMasterTest extends UnitSpec {
  case class TestOrc(schd: JobSchedule = ASAP)
                    (implicit master: OrchestrationMaster) extends JobOrchestration[Unit](schd) {

    private val isFinished: AtomicBoolean = new AtomicBoolean(false)
    def finish(): Unit = isFinished.set(true)

    override private[orchestration] def work: Unit = while (!isFinished.get()) { Thread.sleep(10) }
    override private[orchestration] val orchestrationType = "TEST"
    override private[orchestration] def getNextRecurrence: JobOrchestration[Unit] = TestOrc(schd.getNextSchedule.get)
  }

  def getMaster: OrchestrationMaster = new OrchestrationMaster() {
    override private[orchestration] lazy val pollTimeoutMs = 10
  }

  def waitForPoll(): Unit = Thread.sleep(20)

  "enqueue" should "add orchestration to the queue" in {
    implicit val master: OrchestrationMaster = new OrchestrationMaster()
    val orc = TestOrc()
    master.enqueue(orc)
    master.getQueuedOrchestrations.contains(orc) shouldEqual true
  }

  "nextInLine" should "choose the job orchestration with the earliest schedule" in {
    implicit val master: OrchestrationMaster = getMaster
    val orcEarly = TestOrc(JobSchedule(DateTime.now, JobRecurrence.Once))
    val orcLater = TestOrc(JobSchedule(DateTime.now + 999, JobRecurrence.Once))
    master.nextInLine(orcEarly, orcLater) shouldEqual orcEarly
  }

  "getNextReadyOrchestration" should "return no orchestrations if they are all in the future" in {
    implicit val master: OrchestrationMaster = getMaster
    val orc1 = TestOrc(JobSchedule(DateTime.now + 9999, JobRecurrence.Once))
    val orc2 = TestOrc(JobSchedule(DateTime.now + 1000, JobRecurrence.Daily))
    val orc3 = TestOrc(JobSchedule(DateTime.now + 99999, JobRecurrence.Monthly))
    Seq(orc1, orc2, orc3).foreach(master.enqueue)
    master.getNextReadyOrchestration shouldEqual None
  }

  "getNextReadyOrchestration" should "return the earliest orchestration that is ready" in {
    implicit val master: OrchestrationMaster = getMaster
    val orc1 = TestOrc(JobSchedule(DateTime.now - 1000, JobRecurrence.Once))
    val orc2 = TestOrc(JobSchedule(DateTime.now - 10, JobRecurrence.Daily))
    val orc3 = TestOrc(JobSchedule(DateTime.now - 99999, JobRecurrence.Monthly)) // should be earliest
    Seq(orc1, orc2, orc3).foreach(master.enqueue)
    master.getNextReadyOrchestration shouldEqual Some(orc3)
  }

  "isWorking" should "report when an orchestration is working and when it is not" in {
    implicit val master: OrchestrationMaster = getMaster

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
    implicit val master: OrchestrationMaster = getMaster

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
}
