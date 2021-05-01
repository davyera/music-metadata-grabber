package service.job.spotify

import models.ModelTransform
import models.api.db.{Playlist, Track}
import models.api.resources.spotify.SpotifyPlaylistTracksPage
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** For a given playlist, will request all tracks for that playlist from Spotify.
 *  Optionally pushes Track data (will be missing Lyrics & Audio Features)
 *  Optionally pushes Playlist data once track data is pulled.
 *  @return All track data that was found once the job is finished.
 */
case class PlaylistTracksJob(playlist: Playlist,
                             pushPlaylistData: Boolean,
                             pushTrackData: Boolean)
                            (implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[(Playlist, Seq[Track])] {

  override private[job] val jobName: String = "PLAYLIST_TRACKS"

  override private[job] def work: Future[(Playlist, Seq[Track])] = {
    val plistId = playlist._id
    val plistTag = toTag(playlist.name, plistId)

    spotify.requestPlaylistTracks(plistId).map { playlistTracksPages: Seq[Future[SpotifyPlaylistTracksPage]] =>
      val pagedTracks = workOnPages(playlistTracksPages) { page: SpotifyPlaylistTracksPage =>
        val sTrks = page.items.map(_.track)
        val tracks = sTrks.map(ModelTransform.track)
        if (pushTrackData) tracks.foreach(data.receive)
        tracks
      }

      val tracks = awaitPagedResults(pagedTracks)

      logInfo(s"Gathered ${tracks.size} tracks for playlist $plistTag")

      // concatenate all Track IDs to finalize the playlist object.
      val trackIds = tracks.map(_._id)
      val finalPlist = playlist.copy(tracks = trackIds)
      if(pushPlaylistData) data.receive(finalPlist)

      (finalPlist, tracks)
    }
  }

  override private[job] def recovery: (Playlist, Seq[Track]) = (playlist, Nil)
}