package models.api.webapp

import service.job.orchestration.JobRecurrence.JobRecurrence

object WebappRequest {}

trait Recurrable {
  def getRecurrence: Option[JobRecurrence]
}

abstract class OrchestrationRequest {
  val requiredArgs: Seq[Any]
  val requiredArgNames: Seq[String] = Nil
  def isValid: Boolean = requiredArgs.forall(Option(_).isDefined)
}

case class ArtistOrchestrationRequest(artist_name: String) extends OrchestrationRequest {
  override val requiredArgs: Seq[String] = Seq(artist_name)
  override val requiredArgNames: Seq[String] = Seq("artist_name")
}

case class CategoryPlaylistsOrchestrationRequest(category_id: String,
                                                 recurrence: Option[JobRecurrence])
  extends OrchestrationRequest with Recurrable {
  override def getRecurrence: Option[JobRecurrence] = recurrence
  override val requiredArgs: Seq[String] = Seq(category_id)
  override val requiredArgNames: Seq[String] = Seq("category_id")
}

case class FeaturedPlaylistOrchestrationRequest(recurrence: Option[JobRecurrence])
  extends OrchestrationRequest with Recurrable {
  override def getRecurrence: Option[JobRecurrence] = recurrence
  override val requiredArgs: Seq[String] = Nil
}

