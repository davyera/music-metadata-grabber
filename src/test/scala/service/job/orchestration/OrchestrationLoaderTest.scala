package service.job.orchestration

import akka.http.scaladsl.model.DateTime
import models.OrchestrationSummary
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.{times, verify, when}
import service.data.DataPersistence
import service.job.JobEnvironment

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._

class OrchestrationLoaderTest extends OrchestrationSpec {

  def getLoader(env: JobEnvironment = defaultEnv): OrchestrationLoader = new OrchestrationLoader()(env)
  def getData(orcs: JobOrchestration[_]*): DataPersistence = {
    val data = mock[DataPersistence]
    orcs.foreach { orc => when(data.persistOrchestration(orc.summarize)).thenReturn(Future(true)) }
    when(data.getOrchestrations).thenReturn(Future(Nil))
    data
  }

  "enqueue" should "add orchestration to the queue and to persistence layer" in {
    val data = getData()
    val argCaptor: ArgumentCaptor[OrchestrationSummary] = ArgumentCaptor.forClass(classOf[OrchestrationSummary])

    val jobEnv = env(data)
    val loader = getLoader(jobEnv)

    val orc = TestOrc()(defaultEnv)
    loader.enqueue(orc)
    loader.getAll.contains(orc) shouldEqual true
    verify(data, times(1)).persistOrchestration(argCaptor.capture())
    argCaptor.getAllValues.asScala.toSeq shouldEqual Seq(orc.summarize)
  }

  "dequeue" should "remove orchestration from the queue and remove it from the persistence layer" in {
    val data = getData()
    val argCaptor: ArgumentCaptor[OrchestrationSummary] = ArgumentCaptor.forClass(classOf[OrchestrationSummary])

    val jobEnv = env(data)
    val loader = getLoader(jobEnv)

    val orc = TestOrc()(defaultEnv)
    loader.dequeue(orc)
    loader.getAll.contains(orc) shouldEqual false
    verify(data, times(1)).removeOrchestration(argCaptor.capture())
    argCaptor.getAllValues.asScala.toSeq shouldEqual Seq(orc.summarize)
  }

  "nextInLine" should "choose the job orchestration with the earliest schedule" in {
    val loader = getLoader(env(getData()))
    val orcEarly = TestOrc(JobSchedule(DateTime.now, JobRecurrence.Once))
    val orcLater = TestOrc(JobSchedule(DateTime.now + 999, JobRecurrence.Once))
    loader.nextInLine(orcEarly, orcLater) shouldEqual orcEarly
  }

  "getNextReadyOrchestration" should "return no orchestrations if they are all in the future" in {
    val orc1 = TestOrc(JobSchedule(DateTime.now + 9999, JobRecurrence.Once))
    val orc2 = TestOrc(JobSchedule(DateTime.now + 1000, JobRecurrence.Daily))
    val orc3 = TestOrc(JobSchedule(DateTime.now + 99999, JobRecurrence.Monthly))
    val data = getData(orc1, orc2, orc3)
    val jobEnv = env(data)
    val loader = getLoader(jobEnv)
    Seq(orc1, orc2, orc3).foreach(loader.enqueue)
    loader.getNextInLine shouldEqual None
  }

  "getNextReadyOrchestration" should "return the earliest orchestration that is ready" in {
    val orc1 = TestOrc(JobSchedule(DateTime.now - 1000, JobRecurrence.Once))
    val orc2 = TestOrc(JobSchedule(DateTime.now - 10, JobRecurrence.Daily))
    val orc3 = TestOrc(JobSchedule(DateTime.now - 99999, JobRecurrence.Monthly)) // should be earliest
    val data = getData(orc1, orc2, orc3)
    val jobEnv = env(data)
    val loader = getLoader(jobEnv)
    Seq(orc1, orc2, orc3).foreach(loader.enqueue)
    loader.getNextInLine shouldEqual Some(orc3)
  }

  "initOrchestrationQueue" should "enqueue orchestrations from the data persistence layer" in {
    val time = DateTime.now
    val timeStr = time.toIsoDateString()
    val sum1 = OrchestrationSummary("1", OrchestrationType.ArtistByName, "a1", timeStr, JobRecurrence.Once)
    val sum2 = OrchestrationSummary("2", OrchestrationType.FeaturedPlaylists, "", timeStr, JobRecurrence.Weekly)
    val sum3 = OrchestrationSummary("3", "invalid", "", "", JobRecurrence.Once) // should not be enqueued

    val data = mock[DataPersistence]
    when(data.getOrchestrations).thenReturn(Future(Seq(sum1, sum2, sum3)))

    implicit val jobEnv: JobEnvironment = env(data)
    val loader = getLoader(jobEnv)
    Await.result(loader.getInitializationFuture, 100.millis)

    val orc1 = ArtistOrchestration.byName("a1")
    val orc2 = FeaturedPlaylistsOrchestration(JobSchedule(time, JobRecurrence.Weekly))
    loader.getAll.toSet shouldEqual Set(orc1, orc2)
  }

  "makeOrchestration" should "create an ArtistOrchestration.ByName from the proper summary" in {
    implicit val jobEnv: JobEnvironment = env(getData())
    val sum = OrchestrationSummary("1", OrchestrationType.ArtistByName, "a1", "", JobRecurrence.Once)
    val orc = ArtistOrchestration.byName("a1")
    getLoader(jobEnv).makeOrchestration(sum) shouldEqual Some(orc)
  }

  "makeOrchestration" should "create an ArtistOrchestration.ById from the proper summary" in {
    implicit val jobEnv: JobEnvironment = env(getData())
    val sum = OrchestrationSummary("1", OrchestrationType.ArtistById, "0001", "1989", JobRecurrence.Once)
    val orc = ArtistOrchestration.byId("0001")
    getLoader(jobEnv).makeOrchestration(sum) shouldEqual Some(orc)
  }

  "makeOrchestration" should "create a FeaturedPlaylistsOrchestration from the proper summary" in {
    implicit val jobEnv: JobEnvironment = env(getData())
    val time = DateTime.now
    val sum = OrchestrationSummary("1", OrchestrationType.FeaturedPlaylists, "", time.toIsoDateString(),
      JobRecurrence.Monthly)
    val orc = FeaturedPlaylistsOrchestration(JobSchedule(time, JobRecurrence.Monthly))
    getLoader(jobEnv).makeOrchestration(sum) shouldEqual Some(orc)
  }

  "makeOrchestration" should "create a CategoryPlaylistsOrchestration from the proper summary" in {
    implicit val jobEnv: JobEnvironment = env(getData())
    val time = DateTime.now
    val sum = OrchestrationSummary("1", OrchestrationType.CategoryPlaylists, "c1", time.toIsoDateString(),
      JobRecurrence.Weekly)
    val orc = CategoryPlaylistsOrchestration("c1", JobSchedule(time, JobRecurrence.Weekly))
    getLoader(jobEnv).makeOrchestration(sum) shouldEqual Some(orc)
  }

  "makeOrchestration" should "return None and log error when given an invalid job summary" in {
    implicit val jobEnv: JobEnvironment = env(getData())
    val sum = OrchestrationSummary("0", "NO GOOD", "abc", "2020", JobRecurrence.Once)
    val logVerifier = getLogVerifier[OrchestrationLoader]
    getLoader(jobEnv).makeOrchestration(sum) shouldEqual None
    logVerifier.assertLogged("Invalid Orchestration Type: NO GOOD")
  }
}
