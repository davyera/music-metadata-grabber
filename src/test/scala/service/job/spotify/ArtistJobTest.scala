package service.job.spotify

import models.api.db.Artist
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import service.data.DataPersistence
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class ArtistJobTest extends JobSpec {

  "doWorkBlocking" should "query artist info for an artist ID" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestArtist("art1")).thenReturn(Future(art1))

    val data = mock[DataPersistence]
    val artCaptor: ArgumentCaptor[Artist] = ArgumentCaptor.forClass(classOf[Artist])
    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, data = data)

    val result = ArtistJob("art1", pushArtistData = true).doWorkBlocking()

    verify(data, times(1)).receive(artCaptor.capture())
    assertMetadataSeqs(Seq(art1d), artCaptor.getAllValues)
    result shouldEqual art1d
  }
}
