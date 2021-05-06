package service.job.orchestration

import models.Backend
import service.data.DataPersistence
import service.job.JobEnvironment
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.SpotifyRequester
import testutils.UnitSpec

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.ExecutionContext

class OrchestrationSpec extends UnitSpec {

  private val ctx = context

  val defaultEnv: JobEnvironment = env()

  case class TestOrc(schd: JobSchedule = ASAP)
                    (implicit jobEnv: JobEnvironment = defaultEnv)
    extends JobOrchestration[Unit](schd) {

    private val isFinished: AtomicBoolean = new AtomicBoolean(false)
    private val errorMsg: AtomicReference[String] = new AtomicReference("")
    def finish(): Unit = isFinished.set(true)
    def throwError(msg: String): Unit = errorMsg.set(msg)

    override private[orchestration] def work: Unit =
      while (!isFinished.get()) {
        Thread.sleep(10)
        if (errorMsg.get.nonEmpty)
          throw new Exception(errorMsg.get())
      }
    override private[orchestration] val orchestrationType = "TEST"
    override private[orchestration] def getNextRecurrence: JobOrchestration[Unit] = TestOrc(schd.getNextSchedule.get)
  }

  def env(data: DataPersistence = mock[DataPersistence]): JobEnvironment = {
    new JobEnvironment(data) {
      override val context: ExecutionContext = ctx
      override val backend: Backend = mock[Backend]
      override val spotify: SpotifyRequester = mock[SpotifyRequester]
      override val genius: GeniusRequester = mock[GeniusRequester]
      override val geniusScraper: GeniusLyricsScraper = mock[GeniusLyricsScraper]
      override val jobCoolDownMs: Int = 0
    }
  }
}
