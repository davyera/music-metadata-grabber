package service.job.spotify

import models.api.db.{Playlist, Track}
import models.api.resources.spotify.SpotifyAudioFeaturesPage
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito._
import service.data.DataReceiver
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class CategoryPlaylistsJobTest extends JobSpec {

  "doWork" should "push playlist and track data" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestCategoryPlaylists("cat1"))
      .thenReturn(Future(Seq(Future(cPlistPg1), Future(cPlistPg2))))
    when(spotify.requestPlaylistTracks("p1"))
      .thenReturn(Future(Seq(Future(p1TrksPg1), Future(p1TrksPg2))))
    when(spotify.requestPlaylistTracks("p2"))
      .thenReturn(Future(Seq(Future(p2TrksPg1))))
    when(spotify.requestAudioFeatures(Seq("t1"))).thenReturn(Future(SpotifyAudioFeaturesPage(Seq(trk1f))))
    when(spotify.requestAudioFeatures(Seq("t2"))).thenReturn(Future(SpotifyAudioFeaturesPage(Seq(trk2f))))
    when(spotify.requestAudioFeatures(Seq("t3"))).thenReturn(Future(SpotifyAudioFeaturesPage(Seq(trk3f))))

    val receiver = mock[DataReceiver]
    val trkCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])
    val plistCaptor: ArgumentCaptor[Playlist] = ArgumentCaptor.forClass(classOf[Playlist])

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, dReceiver = receiver)

    val result = CategoryPlaylistsJob("cat1", pushPlaylistData = true, pushTrackData = true).doWork()

    // want 5 invocations, 2 playlists, 3 tracks
    verify(receiver, Mockito.timeout(1000).times(2)).receive(plistCaptor.capture())
    val capturedPlists = plistCaptor.getAllValues
    capturedPlists.contains(plist1cd) shouldBe true
    capturedPlists.contains(plist2cd) shouldBe true

    verify(receiver, Mockito.timeout(1000).times(3)).receive(trkCaptor.capture())
    val capturedTracks = trkCaptor.getAllValues
    capturedTracks.contains(trk1fd) shouldBe true
    capturedTracks.contains(trk2fd) shouldBe true
    capturedTracks.contains(trk3fd) shouldBe true

    whenReady(result) { plistTrackMap: Map[Playlist, Seq[Track]] =>
      plistTrackMap.keySet.size shouldEqual 2
      plistTrackMap.values.flatten.size shouldEqual 3
      plistTrackMap(plist1cd).contains(trk1fd) shouldBe true
      plistTrackMap(plist1cd).contains(trk2fd) shouldBe true
      plistTrackMap(plist2cd).contains(trk3fd) shouldBe true

    }
  }
}
