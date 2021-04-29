package webapp

import models.api.webapp.{ArtistRequest, CategoryRequest, JobSummary}
import play.api.mvc._
import play.api.libs.json._
import service.job.{DataJob, JobEnvironment, JobOrchestrator}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import WebappFormat._

object WebappFormat {
  implicit val jobJson: OFormat[JobSummary] = Json.format[JobSummary]
  implicit val artistRequestJson: OFormat[ArtistRequest] = Json.format[ArtistRequest]
  implicit val categoryResultJson: OFormat[CategoryRequest] = Json.format[CategoryRequest]
}

@Singleton
class MainController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  private val worker: JobOrchestrator = new JobOrchestrator()(ExecutionContext.Implicits.global)
  private val env: JobEnvironment = worker.environment

  def getJobs: Action[AnyContent] = jobsResponse(env.getJobs)

  private def jobsResponse(jobs: Seq[DataJob[_]]): Action[AnyContent] = Action {
    if (jobs.isEmpty)
      NoContent
    else
      Ok(Json.toJson(jobs.map(_.summarize)))
  }

  def pullFeaturedPlaylistsData: Action[AnyContent] = Action {
    worker.launchFeaturedPlaylistsJobs()
    Ok(s"Orchestrating jobs for featured playlist artists...")
  }

  def pullCategoryPlaylistsData: Action[AnyContent] =
    handleRequestBody[CategoryRequest] { categoryRequest =>
      val category = categoryRequest.category
      worker.launchCategoryPlaylistsJob(category)
      s"Orchestrating jobs for $category category playlists..."
    }

  def pullArtistData: Action[AnyContent] =
    handleRequestBody[ArtistRequest] { artistRequest =>
      val artistName = artistRequest.artist_name
      worker.launchArtistDataJobsForName(artistName)
      s"Orchestrating jobs for artist $artistName..."
    }

  private def handleRequestBody[T](onSuccessWithMsg: T => String)
                                  (implicit fmt: OFormat[T]): Action[AnyContent] =
    Action { implicit request =>
      request.body.asJson.flatMap(Json.fromJson[T](_).asOpt) match {
        case Some(requestObject) => Ok(onSuccessWithMsg(requestObject))
        case None => BadRequest
      }
  }

  def deleteData: Action[AnyContent] = Action {
    env.deleteData()
    Ok(s"Clearing database...")
  }
}
