package service.job

import com.typesafe.scalalogging.StrictLogging
import models.{ArtistSummary, LyricsMap, PageableWithTotal}
import models.api.db._
import service.job.genius.{ArtistLyricsJob, GeniusArtistIdJob}
import service.job.spotify._

import scala.concurrent.{ExecutionContext, Future}

class JobOrchestrator(private implicit val context: ExecutionContext) extends StrictLogging {

  implicit val environment: JobEnvironment = new JobEnvironment

  /** Requests all featured Spotify playlists and their tracks.
   *  For each track, will launch a full data job for its artists.
   */
  def launchFeaturedPlaylistsJobs(): Seq[ArtistSummary] =
    launchPlaylistArtistJobs(FeaturedPlaylistsJob(pushPlaylistData = false))

  /** Requests Spotify playlists and their tracks for a specified category.
   *  For each track, will launch a full data job for its artists.
   */
  def launchCategoryPlaylistsJob(categoryId: String): Seq[ArtistSummary] =
    launchPlaylistArtistJobs(CategoryPlaylistsJob(categoryId, pushPlaylistData = false))

  private def launchPlaylistArtistJobs[P <: PageableWithTotal](playlistJob: PlaylistsJob[P])
    : Seq[ArtistSummary] = {

    // pull playlist data
    val playlists = playlistJob.doWorkBlocking()

    // iterate over every track in each playlist
    playlists.flatMap { playlist =>
      // pull all track data for the playlist
      val plistWTracks = PlaylistTracksJob(playlist, pushPlaylistData = true, pushTrackData = false).doWorkBlocking()
      val tracks = plistWTracks._2

      // for each track, launch a full artist metadata job for its artists
      tracks.flatMap(launchArtistDataJobsForTrack)
    }
  }

  private def launchArtistDataJobsForTrack(track: Track): Seq[ArtistSummary] =
    track.artists.map { artistId => launchArtistDataJobs(artistId) }

  /** Requests all data (Artist, Album, Track) for a given artist, given their name. */
  def launchArtistDataJobsForName(artistName: String): ArtistSummary = {
    // first, query spotify for artist ID
    val artistId = SpotifyArtistIdJob(artistName).doWorkBlocking()
    launchArtistDataJobs(artistId)
  }

  /** Requests all data (Artist, Album, Track) for a given artist, given their ID. */
  private def launchArtistDataJobs(artistId: String): ArtistSummary = {
    // get artist metadata
    val artist = ArtistJob(artistId, pushArtistData = false).doWorkBlocking()

    // async call to Genius to load all artist lyrics
    val artistLyricsMap = launchArtistGeniusDataJobs(artist.name)

    // get album metadata (includes track refs)
    val artistWithAlbums = ArtistAlbumsJob(artist, pushArtistData = true).doWorkBlocking()
    val albums = AlbumsJob(artistWithAlbums.albums, pushAlbumData = true).doWorkBlocking()

    // get track metadata (and append audio features)
    val tracks = albums.flatMap { album =>
      val tracks = TracksJob(album.tracks, pushTrackData = false).doWorkBlocking()
      AudioFeaturesJob(tracks, pushTrackData = false).doWorkBlocking()
    }

    // combine spotify and genius data as we wait for all requests to complete
    val tracksWithLyrics = TrackLyricsCombinationJob(Future(tracks), artistLyricsMap, pushTrackData = true)
      .doWorkBlocking()

    ArtistSummary(artist, albums, tracksWithLyrics)
  }

  private def launchArtistGeniusDataJobs(artistName: String): Future[LyricsMap] = {
    val artistId = GeniusArtistIdJob(artistName).doWorkBlocking()

    ArtistLyricsJob(artistId).doWork()
  }
}