package webapp

import models.api.webapp.{JobSummary, ArtistRequest}
import play.api.mvc._
import play.api.libs.json._
import service.job.{DataJob, JobEnvironment, JobOrchestrator}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class MainController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  private val worker: JobOrchestrator = new JobOrchestrator()(ExecutionContext.Implicits.global)
  private val env: JobEnvironment = worker.environment

  implicit val jobJson: OFormat[JobSummary] = Json.format[JobSummary]
  implicit val artistRequestJson: OFormat[ArtistRequest] = Json.format[ArtistRequest]

  def getJobs: Action[AnyContent] = jobsResponse(env.getJobs)

  def pullFeaturedPlaylistsData: Action[AnyContent] = Action {
    worker.launchPlaylistArtistJobs()
    Ok(s"Orchestrating jobs for featured playlist artists...")
  }

  def pullArtistData: Action[AnyContent] = Action { implicit request =>
    request.body.asJson.flatMap(Json.fromJson[ArtistRequest](_).asOpt) match {
      case Some(artistRequest) =>
        val artistName = artistRequest.artist_name
        worker.launchArtistDataJobsForName(artistName)
        Ok(s"Orchestrating jobs for artist $artistName...")
      case None =>
        BadRequest
    }
  }

  private def jobsResponse(jobs: Seq[DataJob[_]]): Action[AnyContent] = Action {
    if (jobs.isEmpty)
      NoContent
    else
      Ok(Json.toJson(jobs.map(_.summarize)))
  }
}
