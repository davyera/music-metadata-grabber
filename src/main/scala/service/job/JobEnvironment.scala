package service.job

import models.Backend
import service.data.{DbPersistence, DataReceiver}
import service.request.genius.{GeniusAuthTokenProvider, GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.{SpotifyAuthTokenProvider, SpotifyRequester}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class JobEnvironment(implicit val context: ExecutionContext) {
  implicit val backend: Backend = AsyncHttpClientFutureBackend()(context)

  private val spotifyAuth: SpotifyAuthTokenProvider = new SpotifyAuthTokenProvider()
  implicit val spotify: SpotifyRequester = new SpotifyRequester(spotifyAuth)

  private val geniusAuth: GeniusAuthTokenProvider = new GeniusAuthTokenProvider()
  implicit val genius: GeniusRequester = new GeniusRequester(geniusAuth)
  implicit val geniusScraper: GeniusLyricsScraper = new GeniusLyricsScraper()

  implicit val receiver: DataReceiver[_] = new DbPersistence()

  private val jobs = mutable.Buffer[DataJob[_]]()

  def registerJob(job: DataJob[_]): Unit = jobs.synchronized(jobs.addOne(job))
  def unfinishedJobs: Seq[DataJob[_]] = jobs.synchronized(jobs.filter(!_.isComplete).toSeq)
  def failedJobs: Seq[DataJob[_]] = jobs.synchronized(jobs.filter(_.isFailed).toSeq)
}
