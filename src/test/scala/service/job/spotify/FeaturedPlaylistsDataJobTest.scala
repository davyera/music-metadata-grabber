package service.job.spotify

import models.api.response._
import models.db.{Playlist, Track}
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{verify, when}
import service.DataReceiver
import service.job.JobFramework
import service.request.spotify.SpotifyRequester
import testutils.JobSpec

import scala.concurrent.Future

class FeaturedPlaylistsDataJobTest extends JobSpec {

  private val art1 = SpotifyArtistRef("artist1", "art1")
  private val art2 = SpotifyArtistRef("artist2", "art2")
  private val art3 = SpotifyArtistRef("artist3", "art3")
  private val alb1 = SpotifyAlbumRef("album1", "alb1")
  private val alb2 = SpotifyAlbumRef("album2", "alb2")

  private val trk1 = SpotifyTrack("t1", "song1", Seq(art1, art2), alb1, 1, 10)
  private val trk1d = Track("t1", "song1", 10, 1, "alb1", Seq("art1", "art2"), Map())
  private val trk2 = SpotifyTrack("t2", "song2", Seq(art2), alb2, 5, 40)
  private val trk2d = Track("t2", "song2", 40, 5, "alb2", Seq("art2"), Map())
  private val trk3 = SpotifyTrack("t3", "song3", Seq(art3), alb2, 1, 100)
  private val trk3d = Track("t3", "song3", 100, 1, "alb2", Seq("art3"), Map())
  private val trk4 = SpotifyTrack("t4", "song4", Seq(art1), alb1, 2, 90)
  private val trk4d = Track("t4", "song4", 90, 2, "alb1", Seq("art1"), Map())
  
  private val plist1 = SpotifyPlaylistInfo("plist1", "p1", "good playlist")
  private val plist1d = Playlist("p1", "plist1", "good playlist", Seq("t1", "t2"))
  private val plist2 = SpotifyPlaylistInfo("plist2", "p2", "bad playlist")
  private val plist2d = Playlist("p2", "plist2", "bad playlist", Seq("t3"))
  private val plist3 = SpotifyPlaylistInfo("plist3", "p3", "ok playlist")
  private val plist3d = Playlist("p3", "plist3", "ok playlist", Seq("t4"))

  private val fPlistPg1 = SpotifyFeaturedPlaylists("hi", SpotifyPlaylistPage(Seq(plist1, plist2), 3))
  private val fPlistPg2 = SpotifyFeaturedPlaylists("hi", SpotifyPlaylistPage(Seq(plist3), 3))

  private val p1TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk1)), 2)
  private val p1TrksPg2 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk2)), 2)
  private val p2TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk3)), 1)
  private val p3TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk4)), 1)

  "doWork" should "push playlist and track data" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestFeaturedPlaylists())
      .thenReturn(Future(Seq(Future(fPlistPg1), Future(fPlistPg2))))
    when(spotify.requestPlaylistTracks("p1"))
      .thenReturn(Future(Seq(Future(p1TrksPg1), Future(p1TrksPg2))))
    when(spotify.requestPlaylistTracks("p2"))
      .thenReturn(Future(Seq(Future(p2TrksPg1))))
    when(spotify.requestPlaylistTracks("p3"))
      .thenReturn(Future(Seq(Future(p3TrksPg1))))

    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])

    implicit val jobFramework: JobFramework = framework(sRequest = spotify, dReceiver = receiver)

    val result = FeaturedPlaylistsDataJob(pushTrackData = true).doWork()

    // want 7 invocations, 3 playlists, 4 tracks
    verify(receiver, Mockito.timeout(1000).times(7)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(plist1d) shouldBe true
    capturedTracks.contains(plist2d) shouldBe true
    capturedTracks.contains(plist3d) shouldBe true
    capturedTracks.contains(trk1d) shouldBe true
    capturedTracks.contains(trk2d) shouldBe true
    capturedTracks.contains(trk3d) shouldBe true
    capturedTracks.contains(trk4d) shouldBe true

    whenReady(result) { plistTrackMap: Map[Playlist, Seq[Track]] =>
      plistTrackMap.keySet.size shouldEqual 3
      plistTrackMap.values.flatten.size shouldEqual 4
      plistTrackMap(plist1d).contains(trk1d) shouldBe true
      plistTrackMap(plist1d).contains(trk2d) shouldBe true
      plistTrackMap(plist2d).contains(trk3d) shouldBe true
      plistTrackMap(plist3d).contains(trk4d) shouldBe true
    }
  }
}
