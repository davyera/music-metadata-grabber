package service.job

import models.Backend
import models.api.response._
import models.db.{Album, Playlist, Track}
import service.DataReceiver
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.SpotifyRequester
import testutils.UnitSpec

import scala.concurrent.ExecutionContext

class JobSpec extends UnitSpec {

  private val ctx = context

  def framework(sRequest: SpotifyRequester = mock[SpotifyRequester],
                gRequest: GeniusRequester = mock[GeniusRequester],
                gScraper: GeniusLyricsScraper = mock[GeniusLyricsScraper],
                dReceiver: DataReceiver = mock[DataReceiver]): JobEnvironment = {
    new JobEnvironment {
      override val context: ExecutionContext = ctx
      override val backend: Backend = mock[Backend]
      override val spotify: SpotifyRequester = sRequest
      override val genius: GeniusRequester = gRequest
      override val geniusScraper: GeniusLyricsScraper = gScraper
      override val receiver: DataReceiver = dReceiver
    }
  }

  /** SPOTIFY TEST DATA */
  private[job] val art1 = SpotifyArtistRef("artist1", "art1")
  private[job] val art2 = SpotifyArtistRef("artist2", "art2")
  private[job] val art3 = SpotifyArtistRef("artist3", "art3")

  lazy private[job] val trk1 = SpotifyTrack("t1", "song1", Seq(art1, art2), alb1r, 1, 10)
  lazy private[job] val trk1d = Track("t1", "song1", 10, 1, "alb1", Seq("art1", "art2"), Map())
  lazy private[job] val trk1ar = SpotifyAlbumTrackRef("song1", "t1", 1)
  lazy private[job] val trk1f = SpotifyAudioFeatures("t1", danceability = 0.5f)
  lazy private[job] val trk1fd = trk1d.copy(features = trk1f.toMap)

  lazy private[job] val trk2 = SpotifyTrack("t2", "song2", Seq(art2), alb2r, 5, 40)
  lazy private[job] val trk2d = Track("t2", "song2", 40, 5, "alb2", Seq("art2"), Map())
  lazy private[job] val trk2ar = SpotifyAlbumTrackRef("song2", "t2", 2)
  lazy private[job] val trk2f = SpotifyAudioFeatures("t2", loudness = 0.2f)
  lazy private[job] val trk2fd = trk2d.copy(features = trk2f.toMap)

  lazy private[job] val trk3 = SpotifyTrack("t3", "song3", Seq(art3), alb2r, 1, 100)
  lazy private[job] val trk3d = Track("t3", "song3", 100, 1, "alb2", Seq("art3"), Map())
  lazy private[job] val trk3ar = SpotifyAlbumTrackRef("song3", "t3", 1)

  lazy private[job] val trk4 = SpotifyTrack("t4", "song4", Seq(art1), alb3r, 2, 90)
  lazy private[job] val trk4d = Track("t4", "song4", 90, 2, "alb3", Seq("art1"), Map())
  lazy private[job] val trk4ar = SpotifyAlbumTrackRef("song4", "t4", 1)

  lazy private[job] val trkPg1 = SpotifyTracks(Seq(trk1, trk2))
  lazy private[job] val trkfPg1 = SpotifyAudioFeaturesPage(Seq(trk2f, trk1f))

  lazy private[job] val alb1r = SpotifyAlbumRef("album1", "alb1")
  lazy private[job] val alb1 = SpotifyAlbum("alb1", "album1", Seq(art1, art2), SpotifyAlbumTracksPage(Seq(trk1ar)), 10)
  lazy private[job] val alb1d = Album("alb1", "album1", 10, Seq("art1", "art2"), Seq("t1"))
  lazy private[job] val alb2r = SpotifyAlbumRef("album2", "alb2")
  lazy private[job] val alb2 = SpotifyAlbum("alb2", "album2", Seq(art2, art3), SpotifyAlbumTracksPage(Seq(trk2ar, trk3ar)), 20)
  lazy private[job] val alb2d = Album("alb2", "album2", 20, Seq("art2", "art3"), Seq("t2", "t3"))
  lazy private[job] val alb3r = SpotifyAlbumRef("album3", "alb3")
  lazy private[job] val alb3 = SpotifyAlbum("alb3", "album3", Seq(art1), SpotifyAlbumTracksPage(Seq(trk4ar)), 30)
  lazy private[job] val alb3d = Album("alb3", "album3", 30, Seq("art1"), Seq("t4"))

  private[job] val albs1 = SpotifyAlbums(Seq(alb1, alb2))
  private[job] val albs2 = SpotifyAlbums(Seq(alb3))
  private[job] val albsAll = SpotifyAlbums(Seq(alb1, alb2, alb3))

  private[job] val artAlbPg1 = SpotifyArtistAlbumsPage(Seq(alb1r), 4)
  private[job] val artAlbPg2 = SpotifyArtistAlbumsPage(Seq(alb2r), 4)
  private[job] val artAlbPg3 = SpotifyArtistAlbumsPage(Seq(alb3r, alb3r), 4) // duplicate here

  private[job] val plist1 = SpotifyPlaylistInfo("plist1", "p1", "good playlist")
  private[job] val plist1d = Playlist("p1", "plist1", "good playlist", Seq("t1", "t2"))
  private[job] val plist2 = SpotifyPlaylistInfo("plist2", "p2", "bad playlist")
  private[job] val plist2d = Playlist("p2", "plist2", "bad playlist", Seq("t3"))
  private[job] val plist3 = SpotifyPlaylistInfo("plist3", "p3", "ok playlist")
  private[job] val plist3d = Playlist("p3", "plist3", "ok playlist", Seq("t4"))

  private[job] val fPlistPg1 = SpotifyFeaturedPlaylists("hi", SpotifyPlaylistPage(Seq(plist1, plist2), 3))
  private[job] val fPlistPg2 = SpotifyFeaturedPlaylists("hi", SpotifyPlaylistPage(Seq(plist3), 3))

  private[job] val p1TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk1)), 2)
  private[job] val p1TrksPg2 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk2)), 2)
  private[job] val p2TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk3)), 1)
  private[job] val p3TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk4)), 1)

}
