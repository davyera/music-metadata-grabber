package service

import com.typesafe.scalalogging.StrictLogging
import service.job.JobOrchestrator

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Server extends App with StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global

  val data = new JobOrchestrator
//  data.orchestrateLyricsJobs("Hazel English")

//  data.orchestrateArtistTrackJobs("1nEGjL7aMVdNQzsfQPKdGr")

  data.orchestrateTrackDataJobs(Seq("2Wv6JOF3gS3cbWWSdbjDBZ", "6dc4H1liPChJmpeXL26OGI"))
}
