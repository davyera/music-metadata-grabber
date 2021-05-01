package service.job

import com.typesafe.scalalogging.StrictLogging
import models.{ArtistSummary, PageableWithTotal}
import service.job.genius.ArtistLyricsJob
import service.job.spotify._

import scala.concurrent.{ExecutionContext, Future}

class JobOrchestrator(private implicit val context: ExecutionContext) extends StrictLogging {

  implicit val environment: JobEnvironment = new JobEnvironment

  /** Requests all featured Spotify playlists and their tracks.
   *  For each track, will launch a full data job for its artists.
   */
  def launchFeaturedPlaylistsJobs(): Set[ArtistSummary] = {
    logger.info("Orchestrating Featured Playlist metadata jobs...")
    launchPlaylistArtistJobs(FeaturedPlaylistsJob(pushPlaylistData = false))
  }

  /** Requests Spotify playlists and their tracks for a specified category.
   *  For each track, will launch a full data job for its artists.
   */
  def launchCategoryPlaylistsJob(categoryId: String): Set[ArtistSummary] = {
    logger.info(s"Orchestrating Category Playlist metadata jobs for category '$categoryId'...")
    launchPlaylistArtistJobs(CategoryPlaylistsJob(categoryId, pushPlaylistData = false))
  }

  private def launchPlaylistArtistJobs[P <: PageableWithTotal](playlistJob: PlaylistsJob[P])
    : Set[ArtistSummary] = {

    // pull playlist data
    val playlists = playlistJob.doWorkBlocking()

    // gather tracks from all playlists
    val allPlaylistTracks = playlists.flatMap { playlist =>
      // pull all track data for the playlist
      val plistWTracks = PlaylistTracksJob(playlist, pushPlaylistData = true, pushTrackData = false).doWorkBlocking()
      plistWTracks._2
    }

    // get all artistIds - and transform to Set to remove duplicates
    val artistIds = allPlaylistTracks.flatMap(_.artists)
    val uniqArtistIds = artistIds.toSet
    val numDuplicates = artistIds.size - uniqArtistIds.size
    val duplicateMsg = if (numDuplicates > 0) s" ($numDuplicates duplicates found)" else ""
    logger.info(s"Found ${uniqArtistIds.size} unique artists across playlists. $duplicateMsg")

    // launch full metadata job for each artist
    // uniqArtistIds.map(launchArtistDataJobs)
    Set.empty
  }

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
    val artistLyricsMapFuture = ArtistLyricsJob(artist.name).doWork()

    // get album metadata (includes track refs)
    val artistWithAlbums = ArtistAlbumsJob(artist, pushArtistData = true).doWorkBlocking()
    // if an album already exists in the DB we can skip it.
    val unIndexedAlbumIds = filterIndexedAlbums(artistWithAlbums.albums)
    val albums = AlbumsJob(unIndexedAlbumIds, pushAlbumData = true).doWorkBlocking()

    // get track metadata (and append audio features)
    val tracks = albums.flatMap { album =>
      val tracks = TracksJob(album.tracks, pushTrackData = false).doWorkBlocking()
      AudioFeaturesJob(tracks, pushTrackData = false).doWorkBlocking()
    }

    // combine spotify and genius data as we wait for all requests to complete
    val tracksWithLyrics =
      TrackLyricsCombinationJob(Future(tracks), artistLyricsMapFuture, pushTrackData = true).doWorkBlocking()

    ArtistSummary(artist, albums, tracksWithLyrics)
  }

  private def filterIndexedAlbums(albumIds: Seq[String]): Seq[String] = {
    // TODO: check if Album has been indexed already, if so we can skip it.
    albumIds
  }
}