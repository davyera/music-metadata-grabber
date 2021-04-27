package service.data

import com.mongodb.client.model.Indexes
import com.mongodb.connection.ClusterSettings
import com.typesafe.scalalogging.StrictLogging
import models.api.db.{Album, Artist, Playlist, Track}
import org.bson.codecs.configuration.CodecRegistries._
import org.mongodb.scala._
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import utils.Configuration

import scala.jdk.CollectionConverters.SeqHasAsJava

class DB(dbName: String = "musicdb", config: Configuration = Configuration) extends StrictLogging {

  // Credentials
  private val userDb = "admin"
  private val username = config.mongoDbUsername
  private val password = config.mongoDbPassword.toCharArray
  private val cred = MongoCredential.createCredential(username, userDb, password)

  // Client setup
  private val hosts = List(ServerAddress(config.mongoDbEndpoint)).asJava
  private val clusterSettings = ClusterSettings.builder.hosts(hosts).build
  private val settings: MongoClientSettings = MongoClientSettings.builder()
    .credential(cred)
    .applyToClusterSettings(t => t.applySettings(clusterSettings))
    .build
  private val mongoClient: MongoClient = MongoClient(settings)

  // Codecs
  private val codecs = fromProviders(classOf[Playlist], classOf[Artist], classOf[Album], classOf[Track])
  private val codecRegistry = fromRegistries(codecs, DEFAULT_CODEC_REGISTRY)

  private val database: MongoDatabase = mongoClient.getDatabase(dbName).withCodecRegistry(codecRegistry)

  // MongoDB Collections
  private[data] val playlists: MongoCollection[Playlist] = database.getCollection("Playlists")
  private[data] val artists: MongoCollection[Artist] = database.getCollection("Artists")
  private[data] val albums: MongoCollection[Album] = database.getCollection("Albums")
  private[data] val tracks: MongoCollection[Track] = database.getCollection("Tracks")

  private[data] val collections = Seq(playlists, artists, albums, tracks)

  def apply(config: Configuration): Unit =
    collections.foreach { collection =>
      logger.info(s"Initializing id and name indexes for collection ${collection.namespace}")
      collection.createIndexes(
        Seq(
          IndexModel(Indexes.ascending("_id"),
            IndexOptions().background(false).unique(true)),
          IndexModel(Indexes.ascending("name"),
            IndexOptions().background(false).unique(false))
        )
      )
    }
}
