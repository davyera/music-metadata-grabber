package service.job.spotify

import models.api.db.Track
import models.api.resources.spotify.SpotifyAudioFeaturesPage
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import service.data.DataReceiver
import service.job.{JobEnvironment, JobSpec}
import service.request.spotify.SpotifyRequester

import scala.concurrent.Future

class AudioFeaturesJobTest extends JobSpec {

  "doWorkBlocking" should "add audio features to input tracks" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestAudioFeatures(Seq("t1", "t2"))).thenReturn(Future(trkfPg1))

    val receiver = mock[DataReceiver]
    val argCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])

    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, dReceiver = receiver)

    val result = AudioFeaturesJob(Seq(trk1d, trk2d), pushTrackData = true).doWorkBlocking()

    val expected = Seq(trk1fd, trk2fd)
    verify(receiver, times(2)).receive(argCaptor.capture())
    assertMetadataSeqs(expected, argCaptor.getAllValues)
    assertMetadataSeqs(expected, result)
  }

  "doWorkBlocking" should "still return data if a track has no audio features" in {
    val spotify = mock[SpotifyRequester]
    when(spotify.requestAudioFeatures(Seq("t1", "t2")))
      .thenReturn(Future(SpotifyAudioFeaturesPage(Seq(trk1f)))) // only one feature returned

    val receiver = mock[DataReceiver]
    val argCaptor: ArgumentCaptor[Track] = ArgumentCaptor.forClass(classOf[Track])

    val logVerifier = getLogVerifier[AudioFeaturesJob]
    implicit val jobEnv: JobEnvironment = env(sRequest = spotify, dReceiver = receiver)

    val result = AudioFeaturesJob(Seq(trk1d, trk2d), pushTrackData = true).doWorkBlocking()

    val expected = Seq(trk1fd, trk2d) // trk2d is not transformed
    verify(receiver, times(2)).receive(argCaptor.capture())
    assertMetadataSeqs(expected, argCaptor.getAllValues)
    assertMetadataSeqs(expected, result)
    logVerifier.assertLogged("SPOTIFY:AUDIO_FEATURES: Could not load audio features for tracks: t2")
  }
}
