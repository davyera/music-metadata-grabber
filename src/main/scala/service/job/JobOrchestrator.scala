package service.job

import com.typesafe.scalalogging.StrictLogging
import models.{ArtistSummary, PageableWithTotal}
import models.api.db.{Album, Artist, Track}
import models.api.resources.spotify.SpotifyPlaylistInfo
import service.job.genius.ArtistFullLyricsJob
import service.job.spotify.{ArtistJob, CategoryPlaylistsJob, FeaturedPlaylistsJob, PlaylistTracksJob, PlaylistsJob, SpotifyArtistIdJob, TracksJob}

import scala.concurrent.{ExecutionContext, Future}

class JobOrchestrator(private implicit val context: ExecutionContext) extends StrictLogging {

  implicit val environment: JobEnvironment = new JobEnvironment

  /** Requests all featured Spotify playlists and their tracks.
   *  For each track, will launch a full data job for its artists.
   */
  def launchFeaturedPlaylistsJobs(): Future[Seq[ArtistSummary]] =
    launchPlaylistArtistJobs(FeaturedPlaylistsJob(pushPlaylistData = true, pushTrackData = false))

  /** Requests Spotify playlists and their tracks for a specified category.
   *  For each track, will launch a full data job for its artists.
   */
  def launchCategoryPlaylistsJob(categoryId: String): Future[Seq[ArtistSummary]] =
    launchPlaylistArtistJobs(CategoryPlaylistsJob(categoryId, pushPlaylistData = true, pushTrackData = false))

  def launchPlaylistTracksJob(playlistId: String): Future[Seq[Track]] = {
    PlaylistTracksJob(playlistId, false).doWork()
  }

  private def launchPlaylistArtistJobs[P <: PageableWithTotal](playlistJob: PlaylistsJob[P])
    : Future[Seq[ArtistSummary]] = {

    // pull playlists -> track data
    val plistMap = playlistJob.doWork()

    // pull artist IDs from the set of tracks
    val artistIdsFuture: Future[Seq[String]] = plistMap.map(_.values.flatten.flatMap(_.artists).toSeq)

    // for each artistId, we'll pull every album and its tracks
    artistIdsFuture.map { artistIds: Seq[String] =>
      val artistSummaries = artistIds.map { artistId =>
        launchArtistDataJobs(artistId)
      }
      Future.sequence(artistSummaries)
    }.flatten
  }

  /** Requests all data (Artist, Album, Track) for a given artist, given their name. */
  def launchArtistDataJobsForName(artistName: String): Future[ArtistSummary] = {
    SpotifyArtistIdJob(artistName).doWork().map { artistId =>
      launchArtistDataJobs(artistId)
    }.flatten
  }

  /** Requests all data (Artist, Album, Track) for a given artist, given their ID. */
  private def launchArtistDataJobs(artistId: String): Future[ArtistSummary] = {
    // ArtistJob will give us Artist and Album info
    ArtistJob(artistId, pushData = true).doWork().map { case (artist: Artist, albums: Seq[Album]) =>
      val trackIds = albums.flatMap(_.tracks)

      // TracksJob will give us detailed Track data (without lyrics)
      val tracks = TracksJob(trackIds, pushData = false).doWork()

      // Concurrently request lyrics from Genius
      val lyricsMap = ArtistFullLyricsJob(artist.name).doWork()

      // Combine the two results when they are ready
      TrackLyricsCombinationJob(tracks, lyricsMap, pushData = true).doWork().map { finalizedTracks: Seq[Track] =>
        ArtistSummary(artist, albums, finalizedTracks)
      }
    }.flatten
  }
}