package service.job

import com.typesafe.scalalogging.StrictLogging
import models.Backend
import service.SimpleScheduledTask
import service.data.{DataReceiver, DbPersistence}
import service.request.genius.{GeniusAuthTokenProvider, GeniusLyricsScraper, GeniusRequester}
import service.request.spotify.{SpotifyAuthTokenProvider, SpotifyRequester}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class JobEnvironment(implicit val context: ExecutionContext) extends StrictLogging {
  implicit val backend: Backend = AsyncHttpClientFutureBackend()(context)

  private val spotifyAuth: SpotifyAuthTokenProvider = new SpotifyAuthTokenProvider()
  private[job] val spotify: SpotifyRequester = new SpotifyRequester(spotifyAuth)

  private val geniusAuth: GeniusAuthTokenProvider = new GeniusAuthTokenProvider()
  private[job] val genius: GeniusRequester = new GeniusRequester(geniusAuth)
  private[job] val geniusScraper: GeniusLyricsScraper = new GeniusLyricsScraper()

  private[job] val receiver: DataReceiver = new DbPersistence()

  private val jobs = new JobHistory()

  def registerJob(job: DataJob[_]): Unit = jobs.add(job)
  def unfinishedJobs: Seq[DataJob[_]] = jobs.get.filterNot(_.isComplete)
  def successfulJobs: Seq[DataJob[_]] = jobs.get.filter(job => job.isComplete && !job.isFailed)
  def failedJobs: Seq[DataJob[_]] = jobs.get.filter(_.isFailed)

  // Periodically remove successful jobs from our cache
  // TODO: make a DB record of these results?
  private val cleanSuccessIntervalMin = 1
  service.SimpleScheduledTask(cleanSuccessIntervalMin, TimeUnit.MINUTES, () => clearSuccessfulJobs())
  private def clearSuccessfulJobs(): Unit = {
    val successes = successfulJobs
    if (successes.nonEmpty) {
      logger.info(s"Cleared ${successes.size} successful jobs from cache")
      successfulJobs.foreach(jobs.remove)
    }
  }
}

private class JobHistory() {
  private val jobs = new java.util.concurrent.ConcurrentHashMap[DataJob[_], Unit]()
  def add(job: DataJob[_]): Unit = jobs.put(job, ())
  def remove(job: DataJob[_]): Unit = jobs.remove(job)
  def get: Seq[DataJob[_]] = jobs.keys.asScala.toSeq
}
