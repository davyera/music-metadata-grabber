package service.job.spotify

import models.api.db.Album
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import service.data.DataPersistence
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class AlbumsJobTest extends JobSpec {

  "doWorkBlocking" should "chunk 2 requests to get album data" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestAlbums(Seq("alb1", "alb2"))).thenReturn(Future(albs1))
    when(spotify.requestAlbums(Seq("alb3"))).thenReturn(Future(albs2))

    val data = mock[DataPersistence]
    val argCaptor: ArgumentCaptor[Album] = ArgumentCaptor.forClass(classOf[Album])

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, data = data)

    val result = AlbumsJob(Seq("alb1", "alb2", "alb3"), pushAlbumData = true, 2).doWorkBlocking()

    val expected = Seq(alb1d, alb2d, alb3d)
    verify(data, times(3)).receive(argCaptor.capture())
    val capturedAlbums = argCaptor.getAllValues
    assertMetadataSeqs(expected, capturedAlbums)
    assertMetadataSeqs(expected, result)
  }
}
