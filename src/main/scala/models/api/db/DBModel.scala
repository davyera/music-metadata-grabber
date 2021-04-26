package models.api.db

private object DbModel {}

abstract class DbEntry

case class Category(_id: String,
                    name: String,
                    playlists: Seq[String]) extends DbEntry

case class Playlist(_id: String,
                    name: String,
                    description: String,
                    tracks: Seq[String]) extends DbEntry

case class Artist(_id: String,
                  name: String,
                  genres: Seq[String],
                  popularity: Int,
                  albums: Seq[String]) extends DbEntry

case class Album(_id: String,
                 name: String,
                 popularity: Int,
                 artists: Seq[String],
                 tracks: Seq[String]) extends DbEntry

case class Track(_id: String,
                 name: String,
                 popularity: Int,
                 track_number: Int,
                 album: String,
                 artists: Seq[String],
                 features: Map[String, Float] = Map(),
                 lyrics: String = "") extends DbEntry
