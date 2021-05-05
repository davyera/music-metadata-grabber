package service

import com.typesafe.scalalogging.StrictLogging
import service.job.orchestration.OrchestrationMaster

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Server extends App with StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global
  implicit val master: OrchestrationMaster = new OrchestrationMaster

  master.enqueueFeaturedPlaylistsOrchestration(None, None)
//  master.enqueueArtistOrchestration("hazel english")
//  master.enqueueArtistOrchestration("day wave")
}
