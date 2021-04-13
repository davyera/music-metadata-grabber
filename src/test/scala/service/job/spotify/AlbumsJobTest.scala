package service.job.spotify

import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito._
import service.DataReceiver
import service.job.{JobFramework, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class AlbumsJobTest extends JobSpec {

  "doWork" should "chunk 2 requests to get album data" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestAlbums(Seq("alb1", "alb2")))
      .thenReturn(Future(albs1))
    when(spotify.requestAlbums(Seq("alb3")))
      .thenReturn(Future(albs2))

    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])

    implicit val jobFramework: JobFramework = framework(sRequest = spotify, dReceiver = receiver)

    val result = AlbumsJob(Seq("alb1", "alb2", "alb3"), 2).doWork() // should be grouped into 2 chunks

    verify(receiver, Mockito.timeout(1000).times(3)).receive(argCaptor.capture())
    val capturedAlbums = argCaptor.getAllValues
    capturedAlbums.contains(alb1d) shouldBe true
    capturedAlbums.contains(alb2d) shouldBe true
    capturedAlbums.contains(alb3d) shouldBe true

    whenReady(result) { albs =>
      albs.contains(alb1d) shouldBe true
      albs.contains(alb2d) shouldBe true
      albs.contains(alb3d) shouldBe true
    }
  }
}