package service.data

import models.db.{Album, Artist, Playlist, Track}
import org.mongodb.scala.{Completed, MongoCollection}

import scala.concurrent.Future

/** Receives data and pushes it to Mongo DB */
class DbPersistence(private[data] val db: DB = new DB) extends DataReceiver[Future[Completed]] {

  override def receive(playlist: Playlist): Future[Completed] = pushToDb(playlist, db.playlists)
  override def receive(artist: Artist): Future[Completed] = pushToDb(artist, db.artists)
  override def receive(album: Album): Future[Completed] = pushToDb(album, db.albums)
  override def receive(track: Track): Future[Completed] = pushToDb(track, db.tracks)

  private def pushToDb[T](item: T, collection: MongoCollection[T]): Future[Completed] = {
    collection.insertOne(item).toFuture()
  }
}