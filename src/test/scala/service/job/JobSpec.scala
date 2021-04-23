package service.job

import models.Backend
import models.api.response._
import models.db._
import service.data.DataReceiver
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.SpotifyRequester
import testutils.UnitSpec

import scala.concurrent.ExecutionContext

class JobSpec extends UnitSpec {

  private val ctx = context

  def env(sRequest: SpotifyRequester = mock[SpotifyRequester],
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

  def mkGeniusSearchResponse(hits: Seq[GeniusSearchHit]): GeniusSearchResponse =
    GeniusSearchResponse(GeniusSearchHits(hits, None))

  /** GENIUS TEST DATA */

  private[job] val gArt1 = GeniusSearchArtist(0, "artist1")
  private[job] val gArt2 = GeniusSearchArtist(10, "artist2")
  private[job] val gSrchHt1 = GeniusSearchHit(GeniusSearchSong(1, "song1", "url1", gArt1))
  private[job] val gSrchHt2 = GeniusSearchHit(GeniusSearchSong(100, "song2", "url2", gArt2))

  private[job] val gTrk1 = GeniusSong(1, "song1", "url1")
  private[job] val gTrk1Lyrics = "lyrics1"
  private[job] val gTrk2 = GeniusSong(2, "song2", "url2")
  private[job] val gTrk2Lyrics = "lyrics2"
  private[job] val gTrk3 = GeniusSong(3, "song3", "url3")
  private[job] val gTrk3Lyrics = "lyrics3"

  private[job] val gTrkPg1 = GeniusArtistSongsPage(GeniusArtistSongs(Seq(gTrk1, gTrk2), None))
  private[job] val gTrkPg2 = GeniusArtistSongsPage(GeniusArtistSongs(Seq(gTrk3), None))

  /** SPOTIFY TEST DATA */
  private[job] val art1 = SpotifyArtist("art1", "artist1", Seq("pop", "hiphop"), 100)
  private[job] val art1d = Artist("art1", "artist1", Seq("pop", "hiphop"), 100, Seq("alb1", "alb2"))
  private[job] val art1r = SpotifyArtistRef("art1", "artist1")
  private[job] val art2 = SpotifyArtist("art2", "artist2", Seq("jazz"), 50)
  private[job] val art2r = SpotifyArtistRef("art2", "artist2")
  private[job] val art3r = SpotifyArtistRef("art3", "artist3")

  private[job] val sSrchArt = SpotifyArtistsSearchPage(Seq(art1, art2), 1)

  private[job] lazy val trk1 = SpotifyTrack("t1", "song1", Seq(art1r, art2r), alb1r, 1, 10)
  private[job] lazy val trk1d = Track("t1", "song1", 10, 1, "alb1", Seq("art1", "art2"), Map())
  private[job] lazy val trk1ar = SpotifyAlbumTrackRef("t1", "song1", 1)
  private[job] lazy val trk1f = SpotifyAudioFeatures("t1", danceability = 0.5f)
  private[job] lazy val trk1fd = trk1d.copy(features = trk1f.toMap)
  private[job] lazy val trk1fld = trk1fd.copy(lyrics = "song1lyrics")

  private[job] lazy val trk2 = SpotifyTrack("t2", "song2", Seq(art2r), alb2r, 5, 40)
  private[job] lazy val trk2d = Track("t2", "song2", 40, 5, "alb2", Seq("art2"), Map())
  private[job] lazy val trk2ar = SpotifyAlbumTrackRef("t2", "song2", 2)
  private[job] lazy val trk2f = SpotifyAudioFeatures("t2", loudness = 0.2f)
  private[job] lazy val trk2fd = trk2d.copy(features = trk2f.toMap)
  private[job] lazy val trk2fld = trk2fd.copy(lyrics = "song2lyrics")

  private[job] lazy val trk3 = SpotifyTrack("t3", "song3", Seq(art3r), alb2r, 1, 100)
  private[job] lazy val trk3d = Track("t3", "song3", 100, 1, "alb2", Seq("art3"), Map())
  private[job] lazy val trk3ar = SpotifyAlbumTrackRef("t3", "song3", 1)
  private[job] lazy val trk3f = SpotifyAudioFeatures("t3", speechiness = 100f)
  private[job] lazy val trk3fd = trk3d.copy(features = trk3f.toMap)

  private[job] lazy val trk4 = SpotifyTrack("t4", "song4", Seq(art1r), alb3r, 2, 90)
  private[job] lazy val trk4d = Track("t4", "song4", 90, 2, "alb3", Seq("art1"), Map())
  private[job] lazy val trk4ar = SpotifyAlbumTrackRef("t4", "song4", 1)
  private[job] lazy val trk4f = SpotifyAudioFeatures("t4", liveness = 2.1f)
  private[job] lazy val trk4fd = trk4d.copy(features = trk4f.toMap)

  private[job] lazy val trkPg1 = SpotifyTracks(Seq(trk1, trk2))
  private[job] lazy val trkfPg1 = SpotifyAudioFeaturesPage(Seq(trk2f, trk1f))

  private[job] lazy val alb1r = SpotifyAlbumRef("alb1", "album1")
  private[job] lazy val alb1 = SpotifyAlbum("alb1", "album1", Seq(art1r, art2r), SpotifyAlbumTracksPage(Seq(trk1ar)), 10)
  private[job] lazy val alb1d = Album("alb1", "album1", 10, Seq("art1", "art2"), Seq("t1"))
  private[job] lazy val alb2r = SpotifyAlbumRef("alb2", "album2")
  private[job] lazy val alb2 = SpotifyAlbum("alb2", "album2", Seq(art2r, art3r), SpotifyAlbumTracksPage(Seq(trk2ar, trk3ar)), 20)
  private[job] lazy val alb2d = Album("alb2", "album2", 20, Seq("art2", "art3"), Seq("t2", "t3"))
  private[job] lazy val alb3r = SpotifyAlbumRef("alb3", "album3")
  private[job] lazy val alb3 = SpotifyAlbum("alb3", "album3", Seq(art1r), SpotifyAlbumTracksPage(Seq(trk4ar)), 30)
  private[job] lazy val alb3d = Album("alb3", "album3", 30, Seq("art1"), Seq("t4"))

  private[job] val albs1 = SpotifyAlbums(Seq(alb1, alb2))
  private[job] val albs2 = SpotifyAlbums(Seq(alb3))
  private[job] val albsAll = SpotifyAlbums(Seq(alb1, alb2, alb3))

  private[job] val artAlbPg0 = SpotifyArtistAlbumsPage(Seq(alb1r, alb2r), 2)
  private[job] val artAlbPg1 = SpotifyArtistAlbumsPage(Seq(alb1r), 4)
  private[job] val artAlbPg2 = SpotifyArtistAlbumsPage(Seq(alb2r), 4)
  private[job] val artAlbPg3 = SpotifyArtistAlbumsPage(Seq(alb3r, alb3r), 4) // duplicate here

  private[job] val plist1 = SpotifyPlaylistInfo("p1", "plist1", "good playlist")
  private[job] val plist1d = Playlist("p1", "plist1", "good playlist", Seq("t1", "t2"))
  private[job] val plist2 = SpotifyPlaylistInfo("p2", "plist2", "bad playlist")
  private[job] val plist2d = Playlist("p2", "plist2", "bad playlist", Seq("t3"))
  private[job] val plist3 = SpotifyPlaylistInfo("p3", "plist3", "ok playlist")
  private[job] val plist3d = Playlist("p3", "plist3", "ok playlist", Seq("t4"))

  private[job] val fPlistPg1 = SpotifyFeaturedPlaylists("hi", SpotifyPlaylistPage(Seq(plist1, plist2), 3))
  private[job] val fPlistPg2 = SpotifyFeaturedPlaylists("hi", SpotifyPlaylistPage(Seq(plist3), 3))

  private[job] val p1TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk1)), 2)
  private[job] val p1TrksPg2 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk2)), 2)
  private[job] val p2TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk3)), 1)
  private[job] val p3TrksPg1 = SpotifyPlaylistTracksPage(Seq(SpotifyPlaylistTrackRef(trk4)), 1)

}
