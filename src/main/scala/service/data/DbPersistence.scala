package service.data

import com.mongodb.BasicDBObject
import com.typesafe.scalalogging.StrictLogging
import models.OrchestrationSummary
import models.api.db.{Album, Artist, MusicMetadata, Playlist, Track}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{Completed, MongoCollection}

import java.util
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/** Receives data and pushes it to Mongo DB in batches */
class DbPersistence(private[data] val db: DB = new DB)
                   (implicit context: ExecutionContext) extends DataPersistence with StrictLogging {

  private val batchSize = 1000
  private val dbPushIntervalMs: Int = 200
  private val cleanResultsIntervalMin = 1

  // schedule tasks for pushing batches and clearing old results
  service.SimpleScheduledTask(dbPushIntervalMs, TimeUnit.MILLISECONDS, () => pushBatches())
  service.SimpleScheduledTask(cleanResultsIntervalMin, TimeUnit.MINUTES, () => clearCompletedResults())

  private val playlistQueue = CollectionQueue(db.playlists)
  private val artistQueue = CollectionQueue(db.artists)
  private val albumQueue = CollectionQueue(db.albums)
  private val trackQueue = CollectionQueue(db.tracks)
  private val queues = Seq(playlistQueue, artistQueue, albumQueue, trackQueue)

  override def persist(playlist: Playlist): Unit = playlistQueue.add(playlist)
  override def persist(artist: Artist): Unit = artistQueue.add(artist)
  override def persist(album: Album): Unit = albumQueue.add(album)
  override def persist(track: Track): Unit = trackQueue.add(track)

  override def persistOrchestration(orchestration: OrchestrationSummary): Future[Boolean] =
    db.orchestrations.insertOne(orchestration).toFuture()
      .map(_ => true)
      .recover(_ => false)

  override def removeOrchestration(orchestration: OrchestrationSummary): Future[Boolean] =
    db.orchestrations.deleteOne(Filters.equal("_id", orchestration._id)).toFuture()
      .map(_.wasAcknowledged())

  override def getAlbumsForArtist(artistId: String): Future[Seq[Album]] =
    db.albums.find(Filters.equal("artists", artistId)).toFuture()

  override def getOrchestrations: Future[Seq[OrchestrationSummary]] =
    db.orchestrations.find().toFuture()

  override def deleteData(): Future[Boolean] = {
    logger.info("Deleting music metadata from DB...")
    val futureResult = Future.sequence {
      db.allCollections.map(c => c.deleteMany(new BasicDBObject()).toFuture())
    }.map(_.forall(_.wasAcknowledged())) // collapse DeleteResult acknowledgements into one boolean
    futureResult.onComplete {
        case Success(_)     => logger.info("Successfully deleted DB data.")
        case Failure(error) => logger.error(s"Could not delete DB data.\n$error")
      }
    futureResult
  }

  def getDbPushResults: Seq[Future[Completed]] = queues.flatMap(_.getResults)

  private def pushBatches(): Unit = queues.foreach(_.pushBatch(batchSize))
  private def clearCompletedResults(): Unit = {
    val numResultsCleared = queues.foldLeft(0)(_ + _.clearCompletedResults())
    if (numResultsCleared > 0)
      logger.info(s"Cleared $numResultsCleared completed DB insert results from cache")
  }
}

private case class CollectionQueue[T](collection: MongoCollection[T]) extends StrictLogging {
  import scala.jdk.CollectionConverters._

  private val itemQueue = new LinkedBlockingQueue[T]()
  private val results = new java.util.concurrent.ConcurrentHashMap[Future[Completed], Unit]()

  def add(item: T): Unit = itemQueue.add(item)

  def getResults: Set[Future[Completed]] = results.keys().asScala.toSet

  def clearCompletedResults(): Int = {
    val completedResults = results.keys.asScala.toSeq.filter(_.isCompleted)
    val count = completedResults.size
    completedResults.foreach(results.remove(_))
    count
  }

  def pushBatch(batchSize: Int): Unit =
    if (itemQueue.size() > 0) {
      val batch: util.LinkedList[T] = new util.LinkedList()
      itemQueue.drainTo(batch, batchSize)

      logger.info(s"Pushing batch of ${batch.size()} documents to ${collection.namespace}.")

      val result = collection.insertMany(batch.asScala.toSeq).toFuture()

      results.put(result, ())
    }
}