package service.job

import models.db.Track

import scala.concurrent.Future
import scala.util.{Failure, Success}

/** Finalization step for track data. Awaits Spotify Tracks result & Genius lyrics result and maps the songs
 *  to each other. Handles processing and error messaging if no lyrics were found, or if more lyrics were
 *  found than necessary.
 */
case class TrackLyricsCombinationJob(tracksFuture: Future[Seq[Track]],
                                     lyricsMapFuture: Future[Map[String, Future[String]]],
                                     pushData: Boolean)
                                    (implicit jobEnvironment: JobEnvironment)
  extends DataJob[Seq[Track]] {

  override private[job] val serviceName = "FINALIZATION"
  override private[job] val jobName = "TRACK_LYRICS"

  override private[job] def work: Future[Seq[Track]] = {
    lyricsMapFuture.map { lyricsMap: Map[String, Future[String]] =>
      tracksFuture.map { tracks: Seq[Track] =>
        // log if we find an imbalance between Spotify and Genius results
        handleResultImbalance(tracks.map(_.name), lyricsMap.keys.toSeq)

        val finalTracks = tracks.map { track =>
          val trkTag = toTag(track.name, track.id)
          // first we handle whether or not the lyricsMap even has an entry for the track
          (lyricsMap.get(track.name) match {
            case Some(lyricResult) => lyricResult
            case None =>
              logError(s"No lyrics found in Genius result map for track $trkTag")
              Future("") // return empty String for lyrics in this case.
          }).transform {
          // then we handle the Future result
            case Success(lyrics)  =>
              // if we were able to find lyrics, add them
              Success(track.addLyrics(lyrics))
            case Failure(error)   =>
              // otherwise, keep track as-is but log an error
              logError(s"Could not load lyrics for track $trkTag. Error:\n${error.getMessage}")
              Success(track)
          }.map { track: Track =>
          // finally, if we need to push the data, then push it.
            if (pushData) pushData(track)
            track
          }
        }
        Future.sequence(finalTracks)
      }.flatten
    }.flatten
  }

  private[job] def handleResultImbalance(sTracks: Seq[String], gTracks: Seq[String]): Unit = {
    val uniqueSTracks = sTracks diff gTracks
    if (uniqueSTracks.nonEmpty)
      logWarn(s"Spotify tracks without Genius lyrics result: ${uniqueSTracks.mkString(", ")}")

    val uniqueGTracks = gTracks diff sTracks
    if (uniqueGTracks.nonEmpty)
      logWarn(s"Genius lyrics result without Spotify track: ${uniqueGTracks.mkString(", ")}")
  }
}
