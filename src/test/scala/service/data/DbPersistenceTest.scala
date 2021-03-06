package service.data

import models.OrchestrationSummary
import models.api.db.{Album, Artist, Playlist, Track}
import org.mongodb.scala.MongoCollection
import service.job.JobSpec

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag

class DbPersistenceTest extends JobSpec {
  val db = new DB("testdb", "testorcdb")
  val dbp = new DbPersistence(db)

  override def beforeAll(): Unit = cleanDb()
  override def afterAll(): Unit = cleanDb()

  private def cleanDb(): Unit = Await.result(dbp.deleteData(), 1.second)

  def dbFindOne[T: ClassTag](collection: MongoCollection[T], key: String, value: String): T =
    dbFind(collection, key, value).head

  def dbFind[T: ClassTag](collection: MongoCollection[T], key: String, value: String): Seq[T] = {
    waitForPersistence()
    Await.result(collection.find(org.mongodb.scala.model.Filters.equal(key, value)).toFuture(), 1.second)
  }

  def assertDocCounts(assertion: Long => Unit): Unit = {
    val future: Future[Seq[Long]] = Future.sequence(db.musicCollections.map(_.countDocuments().toFuture()))
    whenReady(future)(_.foreach (assertion(_)))
  }

  private val track: Track = Track("trk1", "first track", 20, 5, "album1", Seq("artist1"),
    Map("danceability" -> 1, "tempo" -> 120), "la la la")
  private val artist: Artist = Artist("artist1", "artist_name", Seq("pop", "rock"), 50, Seq("album1", "album2"))
  private val album1: Album = Album("album1", "album name", "1990", 5, Seq("art1", "art2"), Seq("trk1", "trk2"))
  private val album2: Album = Album("album2", "album2name", "2000", 10, Seq("art1"), Seq("trk1", "trk5"))
  private val album3: Album = Album("album3", "album3name", "2020", 15, Seq("art2"), Seq("trk3", "trk4"))
  private val plist: Playlist = Playlist("plist1", "playlist hits", "the greatest hits", Seq("trk1", "trk2"), Some("cat"))

  private val orc1: OrchestrationSummary = OrchestrationSummary("orc1", "Artist", "abc", "2020-11-05", "once")
  private val orc2: OrchestrationSummary = OrchestrationSummary("orc2", "Plist", "xyz", "2019-11-19", "weekly")

  "persist" should "push an Artist object to the DB" in {
    dbp.persist(artist)
    dbFindOne(db.artists, "_id", "artist1") shouldEqual artist
  }

  "persist" should "push an Album object to the DB" in {
    dbp.persist(album1)
    dbFindOne(db.albums, "name", "album name") shouldEqual album1
  }

  "persist" should "push a Playlist to the DB" in {
    dbp.persist(plist)
    dbFindOne(db.playlists, "_id", "plist1") shouldEqual plist
  }

  "persist" should "push a Track to the DB" in {
    dbp.persist(track)
    dbFindOne(db.tracks, "name", "first track") shouldEqual track
  }

  "persistOrchestration" should "push an Orchestration Summary to the DB" in {
    whenReady(dbp.persistOrchestration(orc1)) { accepted =>
      accepted shouldEqual true
      dbFindOne(db.orchestrations, "_id", "orc1") shouldEqual orc1
    }
  }

  "removeOrchestration" should "remove an Orchestration Summary from the DB" in {
    whenReady(dbp.persistOrchestration(orc2)) { accepted1 =>
      accepted1 shouldEqual true
      dbFindOne(db.orchestrations, "_id", "orc2") shouldEqual orc2
      whenReady(dbp.removeOrchestration(orc2)) { accepted2 =>
        accepted2 shouldEqual true
        dbFind(db.orchestrations, "_id", "orc2") shouldEqual Nil
      }
    }
  }

  "getOrchestrations" should "return all orchestrations in the DB" in {
    dbp.persistOrchestration(orc1)
    dbp.persistOrchestration(orc2)
    waitForPersistence()
    whenReady(dbp.getOrchestrations) { orcs =>
      orcs.toSet shouldEqual Set(orc1, orc2)
    }
  }

  "getAlbumsForArtist" should "return all albums that contain the artistId" in {
    cleanDb()
    dbp.persist(album1)
    dbp.persist(album2)
    dbp.persist(album3)
    waitForPersistence()
    // should return albums 1&2 but not 3
    whenReady(dbp.getAlbumsForArtist("art1")) { albums =>
      albums.toSet shouldEqual Set(album1, album2)
    }
  }

  "deleteData" should "completely clear the DB collections" in {
    dbp.persist(track)
    dbp.persist(artist)
    dbp.persist(album1)
    dbp.persist(plist)

    val logVerifier = getLogVerifier[DbPersistence]

    //wait, and then make sure each collection has at least 1 document
    waitForPersistence()
    assertDocCounts((count: Long) => count > 0 shouldEqual true)

    whenReady(dbp.deleteData()) { result =>
      result shouldBe true
      logVerifier.assertLogged("Deleting music metadata from DB...", "Successfully deleted music metadata.")
      // after clearData call all counts should be 0
      assertDocCounts((count: Long) => count shouldEqual 0)
    }
  }

  private def waitForPersistence(): Unit = Thread.sleep(250)
}
