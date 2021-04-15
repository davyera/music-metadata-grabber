package service.request.spotify

import models.api.response._
import testutils.APIRequesterSpec

class SpotifyRequesterTest extends APIRequesterSpec {

  private val tokenProvider: SpotifyAuthTokenProvider = new SpotifyAuthTokenProvider()
  private val requester = new SpotifyRequester(tokenProvider)

  "requestCategories" should "return a sequence of spotify categories" in {
    val response = requester.requestCategories()
    verifyPages(response) { page: SpotifyBrowseCategories =>
      page.categories.total should be > 0
      page.categories.items.foreach { category: SpotifyBrowseCategory =>
        category.id shouldNot be (empty)
        category.name shouldNot be (empty)
      }
    }
  }

  "requestCategoryPlaylists" should "return a sequence of spotify playlists" in {
    val category = "hiphop"
    val response = requester.requestCategoryPlaylists(category)
    verifyPages(response) { page: SpotifyCategoryPlaylists =>
      page.playlists.total should be > 0
      page.playlists.items.foreach { playlist: SpotifyPlaylistInfo =>
        playlist.description shouldNot be (empty)
        playlist.id shouldNot be (empty)
        playlist.name shouldNot be (empty)
      }
    }
  }

  "requestFeaturedPlaylists" should "return a valid sequence of spotify playlist API responses" in {
    val response = requester.requestFeaturedPlaylists()
    verifyPages(response) { page: SpotifyFeaturedPlaylists =>
      // simple verification that response values have data
      page.message shouldNot be (empty)
      page.playlists.total should be > 0
      page.playlists.items foreach { playlist: SpotifyPlaylistInfo =>
        playlist.name shouldNot be (empty)
      }
    }
  }

  "requestPlaylistTracks" should "return a valid sequence of track result pages for a playlist" in {
    val testPlaylistId = "3cEYpjA9oz9GiPac4AsH4n"
    val response = requester.requestPlaylistTracks(testPlaylistId, 3)
    verifyPages(response) { page: SpotifyPlaylistTracksPage =>
      page.total shouldEqual 5
      page.items.map(_.track) foreach { track: SpotifyTrack =>
        track.id shouldNot be (empty)
        track.name shouldNot be (empty)
        track.popularity should be >= 0
        track.artists shouldNot be (empty)
        track.artists foreach { artist: SpotifyArtistRef =>
          artist.id shouldNot be(empty)
          artist.name shouldNot be(empty)
        }
      }
    }
  }

  "requestArtist" should "return a valid artist profile" in {
    val testArtistId = "0oSGxfWSnnOXhD2fKuz2Gy" //david bowie
    val response = requester.requestArtist(testArtistId)
    whenReady(response) { artist: SpotifyArtist =>
      artist.id shouldEqual testArtistId
      artist.name shouldEqual "David Bowie"
      artist.popularity should be > 0 // should never fail
      artist.genres should contain ("rock")
    }
  }

  "requestArtistAlbums" should "return a valid sequence of album result pages" in {
    val testArtistIDForAlbums = "1nEGjL7aMVdNQzsfQPKdGr"
    val response = requester.requestArtistAlbums(testArtistIDForAlbums)
    verifyPages(response) { page: SpotifyArtistAlbumsPage =>
      println(page.items.map(_.name))
      page.total should be > 0
      page.items foreach { album: SpotifyAlbumRef =>
        album.id shouldNot be (empty)
        album.name shouldNot be (empty)
      }
    }
  }

  "requestAlbums" should "return a valid sequence of albums" in {
    val testAlbumIDs = Seq("2A2k0MgzDEiFjW3ejlpxxV","4tKnS9Q0lgN3i7SmXi4mGI")
    val response = requester.requestAlbums(testAlbumIDs)
    whenReady(response) { albums: SpotifyAlbums =>
      albums.albums.foreach { album: SpotifyAlbum =>
        album.artists shouldNot be (empty)
        testAlbumIDs should contain (album.id)
        album.name shouldNot be (empty)
        album.popularity should be > 0
        album.tracks.items foreach { track: SpotifyAlbumTrackRef =>
          track.track_number should be > 0
          track.id shouldNot be (empty)
          track.name shouldNot be (empty)
        }
      }
    }
  }

  "requestTracks" should "return a valid sequence of track data" in {
    val testTrackIDs = Seq("2Wv6JOF3gS3cbWWSdbjDBZ","4EWLOLhgJLlfrZb4U55brm")
    val response = requester.requestTracks(testTrackIDs)
    whenReady(response) { tracks: SpotifyTracks =>
      tracks.tracks.foreach { track: SpotifyTrack =>
        track.name shouldNot be (empty)
        testTrackIDs should contain (track.id)
        track.artists shouldNot be (empty)
        track.album.name shouldNot be (empty)
        track.popularity should be > 0
        track.track_number should be > 0
      }
    }
  }

  "requestAudioFeatures" should "return a sequence of audio features for a sequence of track ids" in {
    val testTrackIds = Seq("4JpKVNYnVcJ8tuMKjAj50A","2NRANZE9UCmPAS5XVbXL40")
    val response = requester.requestAudioFeatures(testTrackIds)
    whenReady(response)(_.audio_features.map { audioFeatures: SpotifyAudioFeatures =>
      testTrackIds should contain (audioFeatures.id)
      audioFeatures.duration_ms > 0 shouldEqual true
    })
  }
}
