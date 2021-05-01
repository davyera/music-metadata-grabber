package service.job

import models.LyricsMap
import models.api.db.Track
import utils.MetadataNormalization.normalizeTitle

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Finalization step for track data. Awaits Spotify Tracks result & Genius lyrics result and maps the songs
 *  to each other. Handles processing and error messaging if no lyrics were found, or if more lyrics were
 *  found than necessary.
 */
case class TrackLyricsCombinationJob(tracksFuture: Future[Seq[Track]],
                                     lyricsMapFuture: Future[LyricsMap],
                                     pushTrackData: Boolean)
                                    (implicit jobEnvironment: JobEnvironment)
  extends FinalizationJob[Seq[Track]] {

  override private[job] val jobName = "TRACK_LYRICS"

  override private[job] def work: Future[Seq[Track]] = {
    lyricsMapFuture.map { lyricsMap =>
      if (lyricsMap.isEmpty)
        tracksFuture // skip the job if no lyrics were found.
      else
        tracksFuture.flatMap { tracks => combineTrackLyrics(tracks, lyricsMap) }
    }.flatten
  }

  private def combineTrackLyrics(tracks: Seq[Track], lyricsMap: LyricsMap): Future[Seq[Track]] = {
    // log if we find an imbalance between Spotify and Genius results
    handleResultImbalance(tracks.map(_.name), lyricsMap.keys.toSeq)

    // normalize Genius track titles so we can have some fuzziness when matching between Spotify and Genius
    val normalizedLyricsMap = normalizeLyricsMap(lyricsMap)

    val finalTrackFutures = tracks.map { track =>
      // normalize Spotify track title
      val normalizedTrackName = normalizeTitle(track.name)

      // first we handle whether or not the lyricsMap even has an entry for the track
      (normalizedLyricsMap.get(normalizedTrackName) match {
        case Some(lyricResult)  => lyricResult
        case None               => Future("") // return empty String for lyrics in this case.
      }).transform {
        // then we handle the Future result
        case Success(lyrics)    => Success(track.copy(lyrics = lyrics))
        case Failure(_)         => Success(track) // just return the same input track metadata (sans lyrics)
      }.map { track =>
        if (pushTrackData) data.persist(track)
        track
      }
    }
    Future.sequence(finalTrackFutures)
  }

  /** Normalize the Lyrics map song titles */
  private[job] def normalizeLyricsMap[T](lyricsMap: Map[String, T]): Map[String, T] =
    lyricsMap.map { case (key, value) => normalizeTitle(key) -> value }

  private[job] def handleResultImbalance(sTracks: Seq[String], gTracks: Seq[String]): Unit = {
    val sTracksNormalized = sTracks.map(normalizeTitle)
    val gTracksNormalized = gTracks.map(normalizeTitle)

    val uniqueSTracks = sTracks.filterNot(sTrack => gTracksNormalized.contains(normalizeTitle(sTrack)))
    if (uniqueSTracks.nonEmpty)
      logWarn(s"Spotify tracks without Genius lyrics result: ${uniqueSTracks.mkString(", ")}")

    val uniqueGTracks = gTracks.filterNot(gTrack => sTracksNormalized.contains(normalizeTitle(gTrack)))
    if (uniqueGTracks.nonEmpty)
      logWarn(s"Genius lyrics result without Spotify track: ${uniqueGTracks.mkString(", ")}")
  }

  override private[job] def recovery: Seq[Track] = Nil
}
