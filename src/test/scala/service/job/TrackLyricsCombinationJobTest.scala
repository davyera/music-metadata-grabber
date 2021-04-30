package service.job

import models.api.db.Track
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import service.data.DataReceiver
import utils.MetadataNormalization

import scala.concurrent.Future

class TrackLyricsCombinationJobTest extends JobSpec {

  private def emptyJob =
    TrackLyricsCombinationJob(Future(Nil), Future(Map()), pushTrackData = false)(mock[JobEnvironment])

  "normalizeTrackTitle" should "remove special characters and uppercase from string" in {
    MetadataNormalization.normalizeTitle(" It's Not Real?? (alt title!) _-* ") shouldEqual "itsnotrealalttitle"
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

  "doWorkBlocking" should "match Spotify Tracks to their Genius lyrics result counterpart" in {
    val receiver = mock[DataReceiver]
    val argCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])

    implicit val jobEnv: JobEnvironment = env(dReceiver = receiver)
    val logVerifier = getLogVerifier[TrackLyricsCombinationJob]

    val sTracks = Future(Seq(trk1fd, trk2fd, trk3fd, trk4fd))
    // song 3 should log a failure, song 4 should be missing
    val lMap: Future[Map[String, Future[String]]] = Future(Map(
      "song1" -> Future("song1lyrics"),
      "song2" -> Future("song2lyrics"),
      "song3" -> Future.failed(new Exception("oops"))))

    val result = TrackLyricsCombinationJob(sTracks, lMap, pushTrackData = true).doWorkBlocking()

    val expected = Seq(trk1fld, trk2fld, trk3fd, trk4fd) // trk 3&4 should have no lyrics
    verify(receiver, times(4)).receive(argCaptor.capture())
    assertMetadataSeqs(expected, argCaptor.getAllValues)
    assertMetadataSeqs(expected, result)
    logVerifier.assertLogged(
      "ERROR IN FINALIZATION:TRACK_LYRICS: Could not load lyrics for track song3 (t3). Error:\noops")
    logVerifier.assertLogged(
      "ERROR IN FINALIZATION:TRACK_LYRICS: No lyrics found in Genius result map for track song4 (t4)")
  }
}
