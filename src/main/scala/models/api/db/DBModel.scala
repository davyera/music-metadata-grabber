package models.api.db

private object DbModel {}

abstract class DbEntry(_id: String, name: String)

case class Playlist(_id: String,
                    name: String,
                    description: String,
                    tracks: Seq[String],
                    category: Option[String]) extends DbEntry(_id, name)

case class Artist(_id: String,
                  name: String,
                  genres: Seq[String],
                  popularity: Int,
                  albums: Seq[String]) extends DbEntry(_id, name)

case class Album(_id: String,
                 name: String,
                 popularity: Int,
                 artists: Seq[String],
                 tracks: Seq[String]) extends DbEntry(_id, name)

case class Track(_id: String,
                 name: String,
                 popularity: Int,
                 track_number: Int,
                 album: String,
                 artists: Seq[String],
                 features: Map[String, Float] = Map(),
                 lyrics: String = "") extends DbEntry(_id, name)
