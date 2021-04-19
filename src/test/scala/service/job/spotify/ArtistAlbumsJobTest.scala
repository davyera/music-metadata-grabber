package service.job.spotify

import org.mockito.Mockito._
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class ArtistAlbumsJobTest extends JobSpec {

  "doWork" should "query albums, remove duplicates, then return album info" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestArtistAlbums("art1", 20))
      .thenReturn(Future(Seq(Future(artAlbPg1), Future(artAlbPg2), Future(artAlbPg3))))
    when(spotify.requestAlbums(Seq("alb1", "alb2", "alb3")))
      .thenReturn(Future(albsAll))

    val logVerifier = getLogVerifier[ArtistAlbumsJob]
    implicit val jobEnv: JobEnvironment = env(sRequest = spotify)

    val result = ArtistAlbumsJob("art1", pushData = true).doWork()

    whenReady(result) { albs =>
      albs.contains(alb1d) shouldBe true
      albs.contains(alb2d) shouldBe true
      albs.contains(alb3d) shouldBe true
      logVerifier.assertLogCount(4)
      logVerifier.assertLogged("SPOTIFY:ARTIST_ALBUMS: Filtered duplicate albums for artist art1. Count: 1")
    }
  }
}
