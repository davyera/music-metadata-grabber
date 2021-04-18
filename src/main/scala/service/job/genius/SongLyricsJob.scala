package service.job.genius

import service.job.{GeniusJob, JobEnvironment}

import scala.concurrent.Future

/** Scrapes Genius for lyrics the given song URL.*/
case class SongLyricsJob(url: String)(implicit jobEnvironment: JobEnvironment) extends GeniusJob[String] {
  override private[job] val jobName = "SONG_LYRICS"
  override private[job] def work: Future[String] = geniusScraper.scrapeLyrics(url)
}
