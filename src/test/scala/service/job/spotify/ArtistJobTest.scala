package service.job.spotify

import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito._
import service.DataReceiver
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class ArtistJobTest extends JobSpec {

  "doWork" should "query artist and album info for an artist ID" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestArtist("art1")).thenReturn(Future(art1))
    when(spotify.requestArtistAlbums("art1", 20)).thenReturn(Future(Seq(Future(artAlbPg0))))
    when(spotify.requestAlbums(Seq("alb1", "alb2"))).thenReturn(Future(albs1))

    val logVerifier = getLogVerifier[ArtistJob](classOf[ArtistJob])
    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])
    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, dReceiver = receiver)

    val result = new ArtistJob("art1").doWork()

    verify(receiver, Mockito.timeout(1000).times(3)).receive(argCaptor.capture())
    val capturedArgs = argCaptor.getAllValues
    capturedArgs.contains(art1d) shouldBe true
    capturedArgs.contains(alb1d) shouldBe true
    capturedArgs.contains(alb2d) shouldBe true
    whenReady(result) { art =>
      art shouldEqual art1d
      logVerifier.assertLogged("SPOTIFY:ARTIST: Received full artist data for artist artist1 (art1)")
    }
  }
}
