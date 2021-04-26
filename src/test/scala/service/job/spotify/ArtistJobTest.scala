package service.job.spotify

import models.api.db.{Album, Artist}
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito._
import service.data.DataReceiver
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class ArtistJobTest extends JobSpec {

  "doWork" should "query artist and album info for an artist ID" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestArtist("art1")).thenReturn(Future(art1))
    when(spotify.requestArtistAlbums("art1", 20)).thenReturn(Future(Seq(Future(artAlbPg0))))
    when(spotify.requestAlbums(Seq("alb1", "alb2"))).thenReturn(Future(albs1))

    val logVerifier = getLogVerifier[ArtistJob]
    val receiver = mock[DataReceiver]
    val albCaptor: ArgumentCaptor[Album] = ArgumentCaptor.forClass(classOf[Album])
    val artCaptor: ArgumentCaptor[Artist] = ArgumentCaptor.forClass(classOf[Artist])
    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, dReceiver = receiver)

    val result = ArtistJob("art1", pushData = true).doWork()

    verify(receiver, Mockito.timeout(1000).times(1)).receive(artCaptor.capture())
    val capturedArtists = artCaptor.getAllValues
    capturedArtists.contains(art1d) shouldBe true

    verify(receiver, Mockito.timeout(1000).times(2)).receive(albCaptor.capture())
    val capturedAlbums = albCaptor.getAllValues
    capturedAlbums.contains(alb1d) shouldBe true
    capturedAlbums.contains(alb2d) shouldBe true

    whenReady(result) { case (art: Artist, albums: Seq[Album]) =>
      art shouldEqual art1d
      albums.contains(alb1d) shouldBe true
      albums.contains(alb2d) shouldBe true
      logVerifier.assertLogged("SPOTIFY:ARTIST: Received full artist data for artist artist1 (art1)")
    }
  }
}
