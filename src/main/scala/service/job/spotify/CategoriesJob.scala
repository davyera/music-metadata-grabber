package service.job.spotify

import models.api.resources.spotify.SpotifyBrowseCategories
import service.job.{JobEnvironment, SpotifyJob}

import scala.concurrent.Future

/** Requests all Spotify browse categories. */
case class CategoriesJob()(implicit jobEnvironment: JobEnvironment)
  extends SpotifyJob[Seq[String]] {

  override private[job] val jobName = "CATEGORIES"

  override private[job] def work: Future[Seq[String]] = {
    spotify.requestCategories().map { categoriesPage: Seq[Future[SpotifyBrowseCategories]] =>
      val pageResults = workOnPages(categoriesPage) { categories: SpotifyBrowseCategories =>
        categories.categories.items.map(_.id)
      }
      awaitPagedResults(pageResults)
    }
  }

  override private[job] def recovery: Seq[String] = Nil
}
