package webapp

import play.api.mvc._
import play.api.libs.json._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import WebappFormat._
import models.OrchestrationSummary
import models.api.webapp._
import service.job.orchestration.OrchestrationMaster

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object WebappFormat {
  implicit val jobJson: OFormat[JobSummary] = Json.format[JobSummary]
  implicit val orchestrationJson: OFormat[OrchestrationSummary] = Json.format[OrchestrationSummary]
  implicit val artistRequestJson: OFormat[ArtistOrchestrationRequest] =
    Json.format[ArtistOrchestrationRequest]
  implicit val categoryPlaylistRequestJson: OFormat[CategoryPlaylistsOrchestrationRequest] =
    Json.format[CategoryPlaylistsOrchestrationRequest]
  implicit val featuredPlaylistRequestJson: OFormat[FeaturedPlaylistOrchestrationRequest] =
    Json.format[FeaturedPlaylistOrchestrationRequest]
}

@Singleton
class MainController @Inject()(val controllerComponents: ControllerComponents) extends BaseController {

  private val maxRequestTimeout: FiniteDuration = 10.seconds

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global
  implicit val master: OrchestrationMaster = new OrchestrationMaster()

  def getJobs: Action[AnyContent] = Action {
    val jobs = master.getJobSummaries
    if (jobs.nonEmpty)
      Ok(Json.toJson(jobs))
    else
      NoContent
  }

  def getCurrentOrchestration: Action[AnyContent] = Action {
    master.getCurrentOrchestrationSummary match {
      case Some(summary)  =>  Ok(Json.toJson(summary))
      case None           =>  NoContent
    }
  }

  def getQueuedOrchestrations: Action[AnyContent] = Action {
    val summaries = master.getQueuedOrchestrationSummaries
    if (summaries.nonEmpty)
      Ok(Json.toJson(summaries))
    else
      NoContent
  }

  def pullFeaturedPlaylistsData: Action[AnyContent] =
  handleRequest[FeaturedPlaylistOrchestrationRequest] { playlistRequest =>
    val recurrence = playlistRequest.recurrence
    master.enqueueFeaturedPlaylistsOrchestration(None, recurrence)
    s"Orchestrating jobs for featured playlist artists..."
  }

  def pullCategoryPlaylistsData: Action[AnyContent] =
    handleRequest[CategoryPlaylistsOrchestrationRequest] { categoryRequest =>
      val category = categoryRequest.category_id
      val recurrence = categoryRequest.recurrence
      master.enqueueCategoryPlaylistsOrchestration(category, None, recurrence)
      s"Orchestrating jobs for $category category playlists..."
    }

  def pullArtistData: Action[AnyContent] =
    handleRequest[ArtistOrchestrationRequest] { artistRequest =>
      val artistName = artistRequest.artist_name
      master.enqueueArtistOrchestration(artistName)
      s"Orchestrating jobs for artist $artistName..."
    }

  private def getRequestBody[T <: OrchestrationRequest](request: Request[AnyContent])
                                                       (implicit fmt: OFormat[T]): Option[T] =
    request.body.asJson.flatMap(Json.fromJson[T](_).asOpt)

  def deleteData(): Action[AnyContent] =
    handleRequestAwaitResponse(master.deleteData()) { accepted =>
      if (accepted)
        Ok("Clearing data...")
      else
        InternalServerError("Could not clear data")
    }

  private def handleRequest[T <: OrchestrationRequest](onSuccessWithMsg: T => String)
                                                      (implicit fmt: OFormat[T]): Action[AnyContent] =
    Action { implicit request: Request[AnyContent] =>
      getRequestBody[T](request) match {
        case Some(requestObject)  =>
          if (requestObject.isValid)
            Ok(onSuccessWithMsg(requestObject))
          else
            BadRequest(s"Missing required arguments: ${requestObject.requiredArgNames.mkString(", ")}")
        case None =>  BadRequest
      }
    }

  private def handleRequestAwaitResponse[T](futureResult: Future[T])
                                           (handleResult: T => Result): Action[AnyContent] = Action {
    try {
      val result = Await.result(futureResult, maxRequestTimeout)
      handleResult(result)
    } catch {
      case _: TimeoutException => RequestTimeout
      case t: Throwable => InternalServerError(t.getMessage)
    }
  }
}
