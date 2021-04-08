package models.db

private object DBModel {}

case class Category(id: String,
                    name: String,
                    playlists: Seq[String])

case class Playlist(id: String,
                    name: String,
                    description: String,
                    tracks: Seq[String])

case class Artist(id: String,
                  name: String,
                  genres: Seq[String],
                  popularity: Int)

case class Album(id: String,
                 name: String,
                 popularity: Int,
                 artists: Seq[String])

case class Track(id: String,
                 name: String,
                 popularity: Int,
                 trackNumber: Int,
                 album: String,
                 artists: Seq[String],
                 features: Map[String, Float],
                 lyrics: String)
