package service.data

import com.mongodb.BasicDBObject
import models.db.{Album, Artist, Playlist, Track}
import org.mongodb.scala.MongoCollection
import service.job.JobSpec

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag

class DbPersistenceTest extends JobSpec {
  val db = new DB("testdb")
  val dbp = new DbPersistence(db)

  override def afterAll() {
    // clean the testDB
    val future = Future.sequence(db.collections.map(c => c.deleteMany(new BasicDBObject()).toFuture()))
    Await.result(future, 1.second)
  }

  def dbFind[T: ClassTag](collection: MongoCollection[T], key: String, value: String): T = {
    val future: Future[T] = collection.find(org.mongodb.scala.model.Filters.equal(key, value)).first().toFuture()
    Await.result(future, 1.second)
  }

  "receive" should "push an Artist object to the DB" in {
    val artist: Artist = Artist("artist1", "artist_name", Seq("pop", "rock"), 50, Seq("album1", "album2"))
    whenReady(dbp.receive(artist)) { _ =>
      dbFind(db.artists, "id", "artist1") shouldEqual artist
    }
  }

  "receive" should "push an Album object to the DB" in {
    val album: Album = Album("album1", "album name", 5, Seq("art1", "art2"), Seq("trk1", "trk2"))
    whenReady(dbp.receive(album)) { _ =>
      dbFind(db.albums, "name", "album name") shouldEqual album
    }
  }

  "receive" should "push a Playlist to the DB" in {
    val plist: Playlist = Playlist("plist1", "playlist hits", "the greatest hits", Seq("trk1", "trk2"))
    whenReady(dbp.receive(plist)) { _ =>
      dbFind(db.playlists, "id", "plist1") shouldEqual plist
    }
  }

  "receive" should "push a Track to the DB" in {
    val track: Track = Track("trk1", "first track", 20, 5, "album1", Seq("artist1"),
      Map("danceability" -> 1, "tempo" -> 120), "la la la")
    whenReady(dbp.receive(track)) { _ =>
      dbFind(db.tracks, "name", "first track") shouldEqual track
    }
  }


}
