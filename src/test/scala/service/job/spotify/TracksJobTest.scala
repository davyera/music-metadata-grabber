package service.job.spotify

import models.api.db.Track
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import service.data.DataPersistence
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import java.util
import scala.concurrent.Future

class TracksJobTest extends JobSpec {

  "doWork" should "push track data" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestTracks(Seq("t1", "t2"))).thenReturn(Future(trkPg1))

    val data = mock[DataPersistence]
    val argCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, data = data)

    val result = TracksJob(Seq("t1", "t2"), pushTrackData = true).doWorkBlocking()

    verify(data, times(2)).persist(argCaptor.capture())
    val capturedTracks: util.List[Track] = argCaptor.getAllValues
    capturedTracks.contains(trk1d) shouldBe true
    capturedTracks.contains(trk2d) shouldBe true
    result.contains(trk1d) shouldBe true
    result.contains(trk2d) shouldBe true
  }
}
