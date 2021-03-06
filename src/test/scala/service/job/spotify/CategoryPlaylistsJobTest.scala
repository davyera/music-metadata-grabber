package service.job.spotify

import models.api.db.Playlist
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import service.data.DataPersistence
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class CategoryPlaylistsJobTest extends JobSpec {

  "doWorkBlocking" should "push playlist data" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestCategoryPlaylists("cat1"))
      .thenReturn(Future(Seq(Future(cPlistPg1), Future(cPlistPg2))))

    val plistResult1 = plist1d.copy(category = Some("cat1"))
    val plistResult2 = plist2d.copy(category = Some("cat1"))

    val data = mock[DataPersistence]
    val plistCaptor: ArgumentCaptor[Playlist] = ArgumentCaptor.forClass(classOf[Playlist])

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, data = data)

    val result = CategoryPlaylistsJob("cat1", pushPlaylistData = true).doWorkBlocking()

    val expected = Seq(plistResult1, plistResult2)
    verify(data, times(2)).persist(plistCaptor.capture())
    assertMetadataSeqs(expected, plistCaptor.getAllValues)
    assertMetadataSeqs(expected, result)
  }
}
