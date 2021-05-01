package service.job.spotify

import models.api.db.Playlist
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.{times, verify, when}
import service.data.DataPersistence
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class FeaturedPlaylistsJobTest extends JobSpec {
  private val spotify = mock[SpotifyRequester]
  when(spotify.requestFeaturedPlaylists()).thenReturn(Future(Seq(Future(fPlistPg1), Future(fPlistPg2))))

  "doWorkBlocking" should "push playlist data" in {
    val data = mock[DataPersistence]
    val plistCaptor: ArgumentCaptor[Playlist] = ArgumentCaptor.forClass(classOf[Playlist])

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, data = data)

    val result = FeaturedPlaylistsJob(pushPlaylistData = true).doWorkBlocking()

    val expected = Seq(plist1d, plist2d, plist3d)
    verify(data, times(3)).persist(plistCaptor.capture())
    assertMetadataSeqs(expected, plistCaptor.getAllValues)
    assertMetadataSeqs(expected, result)
  }
}
