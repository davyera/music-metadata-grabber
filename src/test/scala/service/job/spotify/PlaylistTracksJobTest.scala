package service.job.spotify

import models.api.db.{Playlist, Track}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import service.data.DataPersistence
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class PlaylistTracksJobTest extends JobSpec {

  private val spotify = mock[SpotifyRequester]
  when(spotify.requestPlaylistTracks("p1"))
    .thenReturn(Future(Seq(Future(p1TrksPg1), Future(p1TrksPg2))))

  "doWorkBlocking" should "push track and playlist data" in {
    val data = mock[DataPersistence]
    val trkCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])
    val plistCaptor: ArgumentCaptor[Playlist] = ArgumentCaptor.forClass(classOf[Playlist])

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, data = data)

    val result = PlaylistTracksJob(plist1d, pushPlaylistData = true, pushTrackData = true).doWorkBlocking()

    val expectedPlists = Seq(plist1td)
    verify(data, times(1)).receive(plistCaptor.capture())
    assertMetadataSeqs(expectedPlists, plistCaptor.getAllValues)
    result._1 shouldEqual plist1td

    val expectedTrks = Seq(trk1d, trk2d)
    verify(data, times(2)).receive(trkCaptor.capture())
    assertMetadataSeqs(expectedTrks, trkCaptor.getAllValues)
    assertMetadataSeqs(expectedTrks, result._2)
  }
}
