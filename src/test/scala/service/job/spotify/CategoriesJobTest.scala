package service.job.spotify

import org.mockito.Mockito._
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class CategoriesJobTest extends JobSpec {

  "doWorkBlocking" should "return a seq of category IDs" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestCategories()).thenReturn(Future(Seq(Future(catPg1), Future(catPg2))))

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify)

    val result = CategoriesJob().doWorkBlocking()
    val expected = Seq("cat1", "cat2", "cat3")
    assertMetadataSeqs(expected, result)
  }
}
