package service.job.spotify

import models.api.response.SpotifyAudioFeaturesPage
import models.db.{Playlist, Track}
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{verify, when}
import service.DataReceiver
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class FeaturedPlaylistsJobTest extends JobSpec {
  private val spotify = mock[SpotifyRequester]
  when(spotify.requestFeaturedPlaylists()).thenReturn(Future(Seq(Future(fPlistPg1), Future(fPlistPg2))))
  when(spotify.requestPlaylistTracks("p1")).thenReturn(Future(Seq(Future(p1TrksPg1), Future(p1TrksPg2))))
  when(spotify.requestPlaylistTracks("p2")).thenReturn(Future(Seq(Future(p2TrksPg1))))
  when(spotify.requestPlaylistTracks("p3")).thenReturn(Future(Seq(Future(p3TrksPg1))))
  when(spotify.requestAudioFeatures(Seq("t1"))).thenReturn(Future(SpotifyAudioFeaturesPage(Seq(trk1f))))
  when(spotify.requestAudioFeatures(Seq("t2"))).thenReturn(Future(SpotifyAudioFeaturesPage(Seq(trk2f))))
  when(spotify.requestAudioFeatures(Seq("t3"))).thenReturn(Future(SpotifyAudioFeaturesPage(Seq(trk3f))))
  when(spotify.requestAudioFeatures(Seq("t4"))).thenReturn(Future(SpotifyAudioFeaturesPage(Seq(trk4f))))

  "doWork" should "push playlist and track data" in {
    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])

    implicit val jobFramework: JobEnvironment = framework(sRequest = spotify, dReceiver = receiver)

    val result = FeaturedPlaylistsJob(pushTrackData = true).doWork()

    // want 7 invocations, 3 playlists, 4 tracks
    verify(receiver, Mockito.timeout(1000).times(7)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(plist1d) shouldBe true
    capturedTracks.contains(plist2d) shouldBe true
    capturedTracks.contains(plist3d) shouldBe true
    capturedTracks.contains(trk1fd) shouldBe true
    capturedTracks.contains(trk2fd) shouldBe true
    capturedTracks.contains(trk3fd) shouldBe true
    capturedTracks.contains(trk4fd) shouldBe true

    whenReady(result) { plistTrackMap: Map[Playlist, Seq[Track]] =>
      plistTrackMap.keySet.size shouldEqual 3
      plistTrackMap.values.flatten.size shouldEqual 4
      plistTrackMap(plist1d).contains(trk1fd) shouldBe true
      plistTrackMap(plist1d).contains(trk2fd) shouldBe true
      plistTrackMap(plist2d).contains(trk3fd) shouldBe true
      plistTrackMap(plist3d).contains(trk4fd) shouldBe true
    }
  }

  "doWork" should "push playlist data but not push track data or request audio features if pushData arg is false" in {
    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])

    implicit val jobFramework: JobEnvironment = framework(sRequest = spotify, dReceiver = receiver)

    val result = FeaturedPlaylistsJob().doWork()

    // want 3 invocations, 3 playlists, 4 tracks (without features)
    verify(receiver, Mockito.timeout(1000).times(3)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(plist1d) shouldBe true
    capturedTracks.contains(plist2d) shouldBe true
    capturedTracks.contains(plist3d) shouldBe true

    whenReady(result) { plistTrackMap: Map[Playlist, Seq[Track]] =>
      plistTrackMap.keySet.size shouldEqual 3
      plistTrackMap.values.flatten.size shouldEqual 4
      plistTrackMap(plist1d).contains(trk1d) shouldBe true // note we are using the tracks WITHOUT feature maps
      plistTrackMap(plist1d).contains(trk2d) shouldBe true
      plistTrackMap(plist2d).contains(trk3d) shouldBe true
      plistTrackMap(plist3d).contains(trk4d) shouldBe true
    }
  }
}
