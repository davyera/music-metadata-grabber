package service.job


import models.db.Track
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito._
import service.data.DataReceiver

import scala.concurrent.Future

class TrackLyricsCombinationJobTest extends JobSpec {

  private def emptyJob =
    TrackLyricsCombinationJob(Future(Nil), Future(Map()), pushData = false)(mock[JobEnvironment])

  "normalizeTrackTitle" should "remove special characters and uppercase from string" in {
    emptyJob.normalizeTrackTitle(" It's Not Real?? (alt title!) _-* ") shouldEqual "itsnotrealalttitle"
  }

  "normalizeLyricsMap" should "replace the map keys with normalized versions" in {
    val lMap = Map("aaa" -> "", "!x---8_7_6" -> "", "" -> "", "USA 1989" -> "")
    val nMap = Map("aaa" -> "", "x876" -> "", "" -> "", "usa1989" -> "")
    emptyJob.normalizeLyricsMap(lMap) shouldEqual nMap
  }

  "handleResultImbalance" should "log imbalance between Spotify and Genius results" in {
    val job = emptyJob
    val logVerifier = getLogVerifier[TrackLyricsCombinationJob]
    val sTrks = Seq("song 1", "SONG NUMBER 2! (number 2)", "=unique song=", "___Song 3___", "spotify song")
    val gTrks = Seq("song 1", "song number 2 - number 2", "-song3-", "genius song", "genius song 2")
    job.handleResultImbalance(sTrks, gTrks)
    logVerifier.assertLogged(0,
      "FINALIZATION:TRACK_LYRICS: Spotify tracks without Genius lyrics result: =unique song=, spotify song")
    logVerifier.assertLogged(1,
      "FINALIZATION:TRACK_LYRICS: Genius lyrics result without Spotify track: genius song, genius song 2")
  }

  "doWork" should "match Spotify Tracks to their Genius lyrics result counterpart" in {
    val receiver = mock[DataReceiver[_]]
    val argCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])

    implicit val jobEnv: JobEnvironment = env(dReceiver = receiver)
    val logVerifier = getLogVerifier[TrackLyricsCombinationJob]

    val sTracks = Future(Seq(trk1fd, trk2fd, trk3fd, trk4fd))
    // song 3 should log a failure, song 4 should be missing
    val lMap: Future[Map[String, Future[String]]] = Future(Map(
      "song1" -> Future("song1lyrics"),
      "song2" -> Future("song2lyrics"),
      "song3" -> Future.failed(new Exception("oops"))))

    val result = TrackLyricsCombinationJob(sTracks, lMap, pushData = true).doWork()
    verify(receiver, Mockito.timeout(1000).times(4)).receive(argCaptor.capture())
    val capturedArgs = argCaptor.getAllValues
    capturedArgs.contains(trk1fld) shouldEqual true // trk 1 has lyrics
    capturedArgs.contains(trk2fld) shouldEqual true // trk 2 has lyrics
    capturedArgs.contains(trk3fd)  shouldEqual true // trk 3 has no lyrics
    capturedArgs.contains(trk4fd)  shouldEqual true // trk 4 has no lyrics
    whenReady(result) { tracks =>
      tracks.size shouldEqual 4
      tracks.contains(trk1fld) shouldEqual true
      tracks.contains(trk2fld) shouldEqual true
      tracks.contains(trk3fd)  shouldEqual true
      tracks.contains(trk4fd)  shouldEqual true
      logVerifier.assertLogged(
        "ERROR IN FINALIZATION:TRACK_LYRICS: Could not load lyrics for track song3 (t3). Error:\noops")
      logVerifier.assertLogged(
        "ERROR IN FINALIZATION:TRACK_LYRICS: No lyrics found in Genius result map for track song4 (t4)")
    }
  }
}
