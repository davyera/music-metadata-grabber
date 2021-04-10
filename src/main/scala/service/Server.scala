package service

import com.typesafe.scalalogging.StrictLogging
import models._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

object Server extends App with StrictLogging {

  implicit val context: ExecutionContext = ExecutionContext.Implicits.global
  implicit val backend: Backend = AsyncHttpClientFutureBackend()(context)

  val data = new DataJobLauncher()
  data.launchGeniusLyricsJob("Hazel English")

}
