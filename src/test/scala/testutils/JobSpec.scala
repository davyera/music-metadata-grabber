package testutils

import models.Backend
import service.DataReceiver
import service.job.JobFramework
import service.request.genius.{GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.SpotifyRequester

import scala.concurrent.ExecutionContext

class JobSpec extends UnitSpec {

  private val ctx = context

  def framework(sRequest: SpotifyRequester = mock[SpotifyRequester],
                gRequest: GeniusRequester = mock[GeniusRequester],
                gScraper: GeniusLyricsScraper = mock[GeniusLyricsScraper],
                dReceiver: DataReceiver = mock[DataReceiver]): JobFramework = {
    new JobFramework {
      override val context: ExecutionContext = ctx
      override val backend: Backend = mock[Backend]
      override val spotify: SpotifyRequester = sRequest
      override val genius: GeniusRequester = gRequest
      override val geniusScraper: GeniusLyricsScraper = gScraper
      override val receiver: DataReceiver = dReceiver
    }
  }
}
