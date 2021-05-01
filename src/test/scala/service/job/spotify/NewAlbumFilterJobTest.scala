package service.job.spotify

import models.api.db.{Album, Artist}
import org.mockito.Mockito._
import service.data.DbPersistence
import service.job.{JobEnvironment, JobSpec}

import scala.concurrent.Future

class NewAlbumFilterJobTest extends JobSpec {

  "doWorkBlocking" should "not filter albums if no results are returned from DB" in {
    val artist = Artist("99", "bowie", Nil, 0, Seq("alb1", "alb2"))

    val data = mock[DbPersistence]
    when(data.getAlbumsForArtist("99")).thenReturn(Future(Nil))

    implicit val jobEnv: JobEnvironment = env(data = data)
    val result = NewAlbumFilterJob(artist).doWorkBlocking()

    result shouldEqual Seq("alb1", "alb2")
  }

  "doWorkBlocking" should "filter out albums that are already persisted for an artist" in {
    val artist = Artist("99", "bowie", Nil, 0, Seq("alb1", "alb2", "alb3", "alb4"))
    val dbAlb1 = Album("alb2", "album2", "2000", 0, Seq("99"), Nil)
    val dbAlb2 = Album("alb4", "album4", "2000", 0, Seq("99"), Nil)

    val logVerifier = getLogVerifier[NewAlbumFilterJob]

    val data = mock[DbPersistence]
    when(data.getAlbumsForArtist("99")).thenReturn(Future(Seq(dbAlb1, dbAlb2)))

    // albums "alb2" and "alb4" should get filtered out
    implicit val jobEnv: JobEnvironment = env(data = data)
    val result = NewAlbumFilterJob(artist).doWorkBlocking()

    result shouldEqual Seq("alb1", "alb3")
    logVerifier.assertLogged(
      "SPOTIFY:NEW_ALBUM_FILTER: Skipping 2 albums already persisted for artist bowie (99)")
  }

  "doWork" should "return original set of album IDs if DB query fails" in {
    val artist = Artist("99", "bowie", Nil, 0, Seq("alb1", "alb2"))

    val logVerifier = getLogVerifier[NewAlbumFilterJob]

    val data = mock[DbPersistence]
    when(data.getAlbumsForArtist("99")).thenReturn(Future.failed(new Exception("oops")))

    implicit val jobEnv: JobEnvironment = env(data = data)
    val result = NewAlbumFilterJob(artist).doWork()

    whenReady(result) { albums =>
      albums shouldEqual Seq("alb1", "alb2")
      logVerifier.assertLogged("ERROR IN SPOTIFY:NEW_ALBUM_FILTER: oops")
    }
  }
}
