package service.job.orchestration

import models.api.db.{Playlist, Track}
import service.job.spotify.{CategoryPlaylistsJob, FeaturedPlaylistsJob, PlaylistTracksJob, PlaylistsJob}

abstract class PlaylistsOrchestration(schedule: JobSchedule = ASAP)(implicit master: OrchestrationMaster)
  extends JobOrchestration[Seq[Playlist]](schedule) {

  protected def initialPlaylistJob: PlaylistsJob[_]
  override private[orchestration] def work: Seq[Playlist] = {
    // pull playlist data
    val playlists = initialPlaylistJob.doWorkBlocking()

    // gather tracks from all playlists
    val playlistsWithTracks: Seq[(Playlist, Seq[Track])] = playlists.map { playlist =>

      // pull all track data for the playlist
      PlaylistTracksJob(playlist, pushPlaylistData = true, pushTrackData = false).doWorkBlocking()
    }

    val allPlaylistTracks = playlistsWithTracks.flatMap(_._2)

    // get all artistIds - and transform to Set to remove duplicates
    val artistIds = allPlaylistTracks.flatMap(_.artists)
    val uniqueArtistIds = artistIds.toSet
    val numDuplicates = artistIds.size - uniqueArtistIds.size
    val duplicateMsg = if (numDuplicates > 0) s" ($numDuplicates duplicates found)" else ""
    logger.info(s"Found ${uniqueArtistIds.size} unique artists across playlists. $duplicateMsg")

    uniqueArtistIds.foreach { artistId =>
      // enqueue a full artist orchestration for each artist found
      master.enqueue(ArtistOrchestration.byId(artistId))
    }

    playlistsWithTracks.map(_._1)
  }
}

case class FeaturedPlaylistsOrchestration(override val schedule: JobSchedule = ASAP)
                                         (implicit master: OrchestrationMaster)
  extends PlaylistsOrchestration(schedule) {

  override private[orchestration] val orchestrationType: String = "FEATURED_PLAYLISTS"

  override protected def initialPlaylistJob: PlaylistsJob[_] = FeaturedPlaylistsJob()

  override private[orchestration] def getNextRecurrence: FeaturedPlaylistsOrchestration =
    FeaturedPlaylistsOrchestration(schedule.getNextSchedule.get)
}

case class CategoryPlaylistsOrchestration(categoryId: String,
                                          override val schedule: JobSchedule = ASAP)
                                         (implicit master: OrchestrationMaster)
  extends PlaylistsOrchestration(schedule) {

  override private[orchestration] val orchestrationType: String = "CATEGORY_PLAYLISTS"

  override protected val inputParameter: String = categoryId

  override protected def initialPlaylistJob: PlaylistsJob[_] = CategoryPlaylistsJob(categoryId)

  override private[orchestration] def getNextRecurrence: CategoryPlaylistsOrchestration =
    CategoryPlaylistsOrchestration(categoryId, schedule.getNextSchedule.get)
}