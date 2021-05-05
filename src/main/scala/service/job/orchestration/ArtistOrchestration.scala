package service.job.orchestration

import models.ArtistSummary
import service.job.TrackLyricsCombinationJob
import service.job.genius.ArtistLyricsJob
import service.job.orchestration.OrchestrationType.OrchestrationType
import service.job.spotify._

object ArtistOrchestration {
  def byId(artistId: String)(implicit master: OrchestrationMaster): ArtistOrchestration =
    ById(artistId)

  def byName(artistName: String)(implicit master: OrchestrationMaster): ArtistOrchestration =
    ByName(artistName)
}

/** Orchestrates all necessary jobs for Artist metadata. */
abstract class ArtistOrchestration(implicit master: OrchestrationMaster)
  extends JobOrchestration[ArtistSummary] {

  protected def getArtistId: String
  override private[orchestration] def getNextRecurrence: ArtistOrchestration = null // artist jobs never recur

  override private[orchestration] val orchestrationType: OrchestrationType = OrchestrationType.Artist
  override private[orchestration] def work: ArtistSummary = {
    val artistName = inputParameter
    logInfo(s"Orchestrating metadata jobs for artist $artistName")

    // first, query spotify for artist ID
    val artistId = getArtistId

    // get artist metadata
    val artist = ArtistJob(artistId, pushArtistData = false).doWorkBlocking()

    // async call to Genius to load all artist lyrics
    val artistLyricsMapFuture = ArtistLyricsJob(artist.name).doWork()

    // get album metadata (includes track refs)
    val artistWithAlbums = ArtistAlbumsJob(artist, pushArtistData = true).doWorkBlocking()
    // filter out any albums that already exist in the DB.
    val newAlbumIds = NewAlbumFilterJob(artistWithAlbums).doWorkBlocking()
    val newAlbums = AlbumsJob(newAlbumIds, pushAlbumData = true).doWorkBlocking()

    // combine all tracks from all albums
    val trackIds = newAlbums.flatMap(_.tracks)
    val tracks = TracksJob(trackIds, pushTrackData = false).doWorkBlocking()
    val tracksWithFeatures = AudioFeaturesJob(tracks, pushTrackData = false).doWork()

    // combine spotify and genius data as we wait for all requests to complete
    val tracksWithLyrics =
      TrackLyricsCombinationJob(tracksWithFeatures, artistLyricsMapFuture, pushTrackData = true).doWorkBlocking()

    logInfo(s"Orchestration for artist $artistName complete.")
    logInfo(s"Found ${newAlbums.size} new albums and ${tracksWithLyrics.size} tracks.")

    ArtistSummary(artist, newAlbums, tracksWithLyrics)
  }
}

private case class ById(artistId: String)(implicit master: OrchestrationMaster)
  extends ArtistOrchestration {
  override protected def getArtistId: String = artistId
  override protected val inputParameter: String = artistId
}

private case class ByName(artistName: String)(implicit master: OrchestrationMaster)
  extends ArtistOrchestration {
  override protected def getArtistId: String = SpotifyArtistIdJob(artistName).doWorkBlocking()
  override protected val inputParameter: String = artistName
}
