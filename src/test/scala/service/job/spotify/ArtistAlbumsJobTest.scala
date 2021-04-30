package service.job.spotify

import models.api.db.Artist
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import service.data.DataReceiver
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class ArtistAlbumsJobTest extends JobSpec {

  "doWorkBlocking" should "query albums, remove duplicates, then return album info" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestArtistAlbums("art1", 20))
      .thenReturn(Future(Seq(Future(artAlbPg1), Future(artAlbPg2), Future(artAlbPg3))))

    val receiver = mock[DataReceiver]
    val artCaptor: ArgumentCaptor[Artist] = ArgumentCaptor.forClass(classOf[Artist])
    val logVerifier = getLogVerifier[ArtistAlbumsJob]
    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, dReceiver = receiver)

    val result = ArtistAlbumsJob(art1d, pushArtistData = true).doWorkBlocking()

    val expected = Seq(art1ad)
    verify(receiver, times(1)).receive(artCaptor.capture())
    assertMetadataSeqs(expected, artCaptor.getAllValues)
    result shouldEqual art1ad
    logVerifier.assertLogged(
      "SPOTIFY:ARTIST_ALBUMS: Filtered duplicate albums for artist artist1 (art1). Count: 1")
  }
}
