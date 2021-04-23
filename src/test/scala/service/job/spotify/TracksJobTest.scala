package service.job.spotify

import models.api.response.SpotifyAudioFeaturesPage
import models.db.Track
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito._
import service.data.DataReceiver
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class TracksJobTest extends JobSpec {

  "doWork" should "push track data with features" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestTracks(Seq("t1", "t2"))).thenReturn(Future(trkPg1))
    when(spotify.requestAudioFeatures(Seq("t1", "t2"))).thenReturn(Future(trkfPg1))

    val receiver = mock[DataReceiver]
    val argCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, dReceiver = receiver)

    val result = TracksJob(Seq("t1", "t2"), pushData = true).doWork()

    verify(receiver, Mockito.timeout(1000).times(2)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(trk1fd) shouldBe true
    capturedTracks.contains(trk2fd) shouldBe true

    whenReady(result) { tracks =>
      tracks.contains(trk1fd) shouldBe true
      tracks.contains(trk2fd) shouldBe true
    }
  }

  "doWork" should "still return data if a track has no audio features" in {
    val trkPg1Feature = SpotifyAudioFeaturesPage(Seq(trk1f)) // only one feature returned
    val spotify = mock[SpotifyRequester]
    when(spotify.requestTracks(Seq("t1", "t2"))).thenReturn(Future(trkPg1))
    when(spotify.requestAudioFeatures(Seq("t1", "t2"))).thenReturn(Future(trkPg1Feature))

    val receiver = mock[DataReceiver]
    val argCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])

    val logVerifier = getLogVerifier[AudioFeaturesJob]
    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, dReceiver = receiver)

    val result = TracksJob(Seq("t1", "t2"), pushData = true).doWork()

    verify(receiver, Mockito.timeout(1000).times(2)).receive(argCaptor.capture())
    val capturedTracks = argCaptor.getAllValues
    capturedTracks.contains(trk1fd) shouldBe true
    capturedTracks.contains(trk2d) shouldBe true //version of trk1 without feature data
    capturedTracks.contains(trk2fd) shouldBe false

    whenReady(result) { tracks =>
      tracks.contains(trk1fd) shouldBe true
      tracks.contains(trk2d) shouldBe true
      logVerifier.assertLogged("SPOTIFY:AUDIO_FEATURES: Could not load audio features for tracks: t2")
    }
  }
}
