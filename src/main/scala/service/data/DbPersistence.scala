package service.data

import com.typesafe.scalalogging.StrictLogging
import models.db.{Album, Artist, Playlist, Track}
import org.mongodb.scala.{Completed, MongoCollection}

import java.util
import java.util.TimerTask
import java.util.concurrent.{LinkedBlockingQueue, ScheduledThreadPoolExecutor, TimeUnit}
import scala.concurrent.Future

/** Receives data and pushes it to Mongo DB in batches */
class DbPersistence(private[data] val db: DB = new DB) extends DataReceiver with StrictLogging {

  private val batchSize = 1000
  private val dbPushIntervalMs: Int = 200
  private val cleanResultsIntervalMin = 1

  // schedule a task for pushing batches and clearing old results
  private val executor = new ScheduledThreadPoolExecutor(1)
  private val batchTask = new TimerTask { override def run(): Unit = pushBatches() }
  executor.scheduleAtFixedRate(batchTask, dbPushIntervalMs, dbPushIntervalMs, TimeUnit.MILLISECONDS)
  private val cleanTask = new TimerTask { override def run(): Unit = clearFinishedResults() }
  executor.scheduleAtFixedRate(cleanTask, cleanResultsIntervalMin, cleanResultsIntervalMin, TimeUnit.MINUTES)

  private val playlistQueue = CollectionQueue(db.playlists)
  private val artistQueue = CollectionQueue(db.artists)
  private val albumQueue = CollectionQueue(db.albums)
  private val trackQueue = CollectionQueue(db.tracks)
  private val queues = Seq(playlistQueue, artistQueue, albumQueue, trackQueue)

  override def receive(playlist: Playlist): Unit = playlistQueue.add(playlist)
  override def receive(artist: Artist): Unit = artistQueue.add(artist)
  override def receive(album: Album): Unit = albumQueue.add(album)
  override def receive(track: Track): Unit = trackQueue.add(track)

  def getDbPushResults: Seq[Future[Completed]] = queues.flatMap(_.getResults)

  private def pushBatches(): Unit = queues.foreach(_.pushBatch(batchSize))
  private def clearFinishedResults(): Unit = queues.foreach(_.clearCompletedResults())
}

private case class CollectionQueue[T](collection: MongoCollection[T]) extends StrictLogging {
  import scala.jdk.CollectionConverters._

  private val itemQueue = new LinkedBlockingQueue[T]()
  private val results = new java.util.concurrent.ConcurrentHashMap[Future[Completed], Unit]()

  def add(item: T): Unit = itemQueue.add(item)

  def getResults: Set[Future[Completed]] = results.keys().asScala.toSet

  def clearCompletedResults(): Unit =
    results.keys().asScala.foreach { result =>
      if (result.isCompleted) results.remove(result)
    }

  def pushBatch(batchSize: Int): Unit = {
    if (itemQueue.size() > 0) {
      val batch: util.LinkedList[T] = new util.LinkedList()
      itemQueue.drainTo(batch, batchSize)

      logger.info(s"Pushing batch of ${batch.size()} documents to ${collection.namespace}.")

      val result = collection.insertMany(batch.asScala.toSeq).toFuture()

      results.put(result, ())
    }
  }
}