package service.job


import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito._
import service.DataReceiver

import scala.concurrent.Future
import scala.util.Failure

class TrackLyricsCombinationJobTest extends JobSpec {

  "handleResultImbalance" should "log imbalance between Spotify and Genius results" in {
    implicit val jobEnv: JobEnvironment = mock[JobEnvironment]
    val job = TrackLyricsCombinationJob(Future(Nil), Future(Map()), pushData = false)
    val logVerifier = getLogVerifier[TrackLyricsCombinationJob](classOf[TrackLyricsCombinationJob])
    val sTrks = Seq("a", "b", "d")
    val gTrks = Seq("b", "c")
    job.handleResultImbalance(sTrks, gTrks)
    logVerifier.assertLogged(0,
      "FINALIZATION:TRACK_LYRICS: Spotify tracks without Genius lyrics result: a, d")
    logVerifier.assertLogged(1,
      "FINALIZATION:TRACK_LYRICS: Genius lyrics result without Spotify track: c")
  }

  "doWork" should "match Spotify Tracks to their Genius lyrics result counterpart" in {
    val receiver = mock[DataReceiver]
    val argCaptor = ArgumentCaptor.forClass(classOf[DataReceiver])

    implicit val jobEnv: JobEnvironment = env(dReceiver = receiver)
    val logVerifier = getLogVerifier[TrackLyricsCombinationJob](classOf[TrackLyricsCombinationJob])

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
