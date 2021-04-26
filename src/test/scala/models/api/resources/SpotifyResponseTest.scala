package models.api.resources

import models.api.resources.spotify.SpotifyAudioFeatures
import testutils.UnitSpec

class SpotifyResponseTest extends UnitSpec {
  "SpotifyAudioFeatures.toMap" should "turn case class into a map of its keys and values" in {
    val featuresObj = SpotifyAudioFeatures(
      id                = "fid",
      danceability      = 0.1f,
      energy            = 0.2f,
      key               = 0.3f,
      loudness          = 0.4f,
      mode              = 0.5f,
      speechiness       = 0.6f,
      acousticness      = 0.7f,
      instrumentalness  = 0.8f,
      liveness          = 0.9f,
      valence           = 1.0f,
      tempo             = 1.1f,
      duration_ms       = 1.2f,
      time_signature    = 1.3f)
    val featureMap = Map(
      "danceability"    -> 0.1f,
      "energy"          -> 0.2f,
      "key"             -> 0.3f,
      "loudness"        -> 0.4f,
      "mode"            -> 0.5f,
      "speechiness"     -> 0.6f,
      "acousticness"    -> 0.7f,
      "instrumentalness"-> 0.8f,
      "liveness"        -> 0.9f,
      "valence"         -> 1.0f,
      "tempo"           -> 1.1f,
      "duration_ms"     -> 1.2f,
      "time_signature"  -> 1.3f)

    featuresObj.toMap shouldEqual featureMap
  }
}
