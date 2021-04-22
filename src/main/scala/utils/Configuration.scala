package utils

import com.typesafe.config.ConfigFactory


object Configuration extends Configuration

class Configuration {

  private val config = ConfigFactory.load("application.conf")

  lazy val spotifyClientId: String = config.getString("spotify_client_id")
  lazy val spotifySecretId: String = config.getString("spotify_secret_id")

  lazy val geniusClientId: String = config.getString("genius_client_id")
  lazy val geniusSecretId: String = config.getString("genius_secret_id")
  lazy val geniusAuthToken: String = config.getString("genius_auth_token")

  lazy val httpRequestRetryTimeMS: Int = config.getInt("http_request_retry_time_ms")

  lazy val mongoDbEndpoint: String = config.getString("mongo_db_endpoint")
  lazy val mongoDbUsername: String = config.getString("mongo_db_username")
  lazy val mongoDbPassword: String = config.getString("mongo_db_password")
}
