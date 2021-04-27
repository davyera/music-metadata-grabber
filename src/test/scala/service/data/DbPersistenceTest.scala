package service.data

import models.api.db.{Album, Artist, Playlist, Track}
import org.mongodb.scala.MongoCollection
import service.job.JobSpec

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag

class DbPersistenceTest extends JobSpec {
  val db = new DB("testdb")
  val dbp = new DbPersistence(db)

  override def beforeAll(): Unit = cleanDb()
  override def afterAll(): Unit = cleanDb()

  private def cleanDb(): Unit = Await.result(dbp.deleteData(), 1.second)

  def dbFind[T: ClassTag](collection: MongoCollection[T], key: String, value: String): T = {
    Thread.sleep(250)
    val future: Future[T] = collection.find(org.mongodb.scala.model.Filters.equal(key, value)).first().toFuture()
    Await.result(future, 1.second)
  }

  def assertDocCounts(assertion: Long => Unit): Unit = {
    val future: Future[Seq[Long]] = Future.sequence(db.collections.map(_.countDocuments().toFuture()))
    whenReady(future)(_.foreach (assertion(_)))
  }

  private val track: Track = Track("trk1", "first track", 20, 5, "album1", Seq("artist1"),
    Map("danceability" -> 1, "tempo" -> 120), "la la la")
  private val artist: Artist = Artist("artist1", "artist_name", Seq("pop", "rock"), 50, Seq("album1", "album2"))
  private val album: Album = Album("album1", "album name", 5, Seq("art1", "art2"), Seq("trk1", "trk2"))
  private val plist: Playlist = Playlist("plist1", "playlist hits", "the greatest hits", Seq("trk1", "trk2"), Some("cat"))


  "receive" should "push an Artist object to the DB" in {
    dbp.receive(artist)
    dbFind(db.artists, "_id", "artist1") shouldEqual artist
  }

  "receive" should "push an Album object to the DB" in {
    dbp.receive(album)
    dbFind(db.albums, "name", "album name") shouldEqual album
  }

  "receive" should "push a Playlist to the DB" in {
    dbp.receive(plist)
    dbFind(db.playlists, "_id", "plist1") shouldEqual plist
  }

  "receive" should "push a Track to the DB" in {
    dbp.receive(track)
    dbFind(db.tracks, "name", "first track") shouldEqual track
  }

  "clearData" should "completely clear the DB collections" in {
    dbp.receive(track)
    dbp.receive(artist)
    dbp.receive(album)
    dbp.receive(plist)

    //wait, and then make sure each collection has at least 1 document
    Thread.sleep(250)
    assertDocCounts((count: Long) => count > 0 shouldEqual true)

    whenReady(dbp.deleteData()) { result =>
      result shouldBe true
      // after clearData call all counts should be 0
      assertDocCounts((count: Long) => count shouldEqual 0)
    }
  }
}
