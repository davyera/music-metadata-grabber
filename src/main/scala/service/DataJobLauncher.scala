package service

import com.typesafe.scalalogging.StrictLogging
import models.Backend
import models.api.response._
import models.db.{Lyrics, Playlist, Track}
import service.request.genius.{GeniusAuthTokenProvider, GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.{SpotifyAuthTokenProvider, SpotifyRequester}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

class DataJobLauncher(implicit val context: ExecutionContext) extends StrictLogging {

  private val MAX_JOB_TIMEOUT_MS = 2000.milliseconds

  private implicit val backend: Backend = AsyncHttpClientFutureBackend()(context)

  private val spotifyAuth: SpotifyAuthTokenProvider = new SpotifyAuthTokenProvider()
  private[service] val spotify: SpotifyRequester = new SpotifyRequester(spotifyAuth)

  private val geniusAuth: GeniusAuthTokenProvider = new GeniusAuthTokenProvider()
  private[service] val genius: GeniusRequester = new GeniusRequester(geniusAuth)
  private[service] val geniusLyrics: GeniusLyricsScraper = new GeniusLyricsScraper()

  private[service] val receiver: DataReceiver = new DataReceiver()

  def orchestratePlaylistDataJobs(): Future[Unit] = {
    // pull featured playlists

    // query each playlist for its tracks

    // for each track, query the artist

    // for each artist, query their albums

    // for each album, query its tracks

    // for each track, query its features
    Future.successful()
  }

  private[service] def launchFeaturedPlaylistsDataJob(pushTrackData: Boolean = false): Future[Unit] = {
    spotify.requestFeaturedPlaylists().map { playlistPages: Seq[Future[SpotifyFeaturedPlaylists]] =>
      launchPagedJobs(playlistPages) { page: SpotifyFeaturedPlaylists =>
        logger.info(s"SPOTIFY: Received page of featured playlists. Count: ${page.playlists.items.size}")
        page.playlists.items.map { playlist: SpotifyPlaylistInfo =>
          val tracksFuture = launchPlaylistTracksJob(playlist, pushTrackData)
          // once we have finished querying all tracks, we can send the full playlist data out
          tracksFuture.map { tracks =>
            val data = Playlist(playlist.id, playlist.name, playlist.description, tracks)
            receiver.receive(data)
          }
        }
      }
    }
  }

  private def launchPlaylistTracksJob(playlist: SpotifyPlaylistInfo, pushData: Boolean = false): Future[Seq[String]] = {
    spotify.requestPlaylistTracks(playlist.id).map { playlistTracksPages: Seq[Future[SpotifyPlaylistTracksPage]] =>
      val pagedTrackIds = launchPagedJobs(playlistTracksPages) { page: SpotifyPlaylistTracksPage =>
        logger.info(s"SPOTIFY: Received page of tracks for playlist ${playlist.name} (${playlist.id}). Count: ${page.items.size}")
        val trackIds = page.items.map { trackRef: SpotifyPlaylistTrackRef =>
          val track = trackRef.track
          if (pushData)
            receiver.receive(Track(track.id, track.name, track.popularity, track.track_number, track.album.id,
              track.artists.map(_.id), Map()))
          track.id
        }
        trackIds
      }

      // Here we block for the jobs to finish -- and flatten the sequences of track IDs to return to the parent job
      val trackIds = pagedTrackIds.flatten(Await.result(_, MAX_JOB_TIMEOUT_MS))
      logger.info(s"SPOTIFY: Gathered ${trackIds.size} track IDs for playlist ${playlist.name} (${playlist.id})")
      trackIds
    }
  }

  /** Performs a full lyrics-scraping job for a given artist
   */
  def orchestrateLyricsJobs(artistName: String): Future[Unit] = {
    // first, we need to query to find the artist ID
    launchGeniusArtistIdJob(artistName).map { artistId: Int =>
      // then, perform a Genius request for all of the artist's songs
      launchGeniusArtistLyricsJob(artistId, artistName)
    }
  }

  /** Performs a Genius search request to extract an artist's ID
   *  @return Genius ID for artist (or -1 if not found)
   */
  private[service] def launchGeniusArtistIdJob(artistName: String): Future[Int] = {
    // initiate a search to find artist's ID -- search with one result
    genius.requestSearchPage(artistName, 1).map { searchResult: GeniusSearchResponse =>
      val hits = searchResult.response.hits
      if (hits.isEmpty)
        throw JobException(s"GENIUS: No search results for artist name $artistName")
      else {
        val id = hits.head.result.primary_artist.id
        logger.info(s"GENIUS: Queried ID for artist $artistName: $id")
        id
      }
    }
  }

  /** Processes scraping all lyrics for a given artist (given an artist ID that has already been queried)
   */
  private def launchGeniusArtistLyricsJob(artistId: Int, artistName: String): Future[Unit] = {
    genius.requestArtistSongs(artistId).map { songsResponsePages: Seq[Future[GeniusArtistSongsPage]] =>
      launchPagedJobs(songsResponsePages) { page: GeniusArtistSongsPage =>
        logger.info(s"GENIUS: Received page of ${page.response.songs.size} songs for artist $artistName ($artistId)")
        page.response.songs.map { song =>
          launchGeniusSongLyricsJob(song, artistName, artistId)
        }
      }
    }
  }

  /** Scrapes Genius for the given song. Requires artist and artist ID info for the data push.
   */
  private def launchGeniusSongLyricsJob(song: GeniusSong, artist: String, artistId: Int): Future[Unit] = {
    geniusLyrics.scrapeLyrics(song.url).map { lyrics: String =>
      val lyricsData = Lyrics(lyrics, song.id, song.title, artistId, artist, song.url)
      receiver.receive(lyricsData)
    }
  }

  private def launchPagedJobs[P, O](pages: Seq[Future[P]])(pageJob: P => O): Seq[Future[O]] = {
    pages.map { pageFuture: Future[P] =>
      pageFuture.map { page: P =>
        pageJob(page)
      }
    }
  }
}

case class JobException(msg: String) extends Exception(msg)