package service

import com.typesafe.scalalogging.StrictLogging
import service.job.JobOrchestrator

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Server extends App with StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global

  val worker = new JobOrchestrator
//  data.orchestrateLyricsJobs("Hazel English")

//  data.orchestrateArtistTrackJobs("1nEGjL7aMVdNQzsfQPKdGr")

  worker.launchPlaylistArtistJobs()
}
