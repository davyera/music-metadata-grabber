package models.db

private object DbModel {}

abstract class DbEntry

case class Category(id: String,
                    name: String,
                    playlists: Seq[String]) extends DbEntry

case class Playlist(id: String,
                    name: String,
                    description: String,
                    tracks: Seq[String]) extends DbEntry

case class Artist(id: String,
                  name: String,
                  genres: Seq[String],
                  popularity: Int) extends DbEntry

case class Album(id: String,
                 name: String,
                 popularity: Int,
                 artists: Seq[String]) extends DbEntry

case class Track(id: String,
                 name: String,
                 popularity: Int,
                 trackNumber: Int,
                 album: String,
                 artists: Seq[String],
                 features: Map[String, Float] = Map()) extends DbEntry

case class Lyrics(lyrics: String,
                  trackId: Int,
                  trackName: String,
                  artistId: Int,
                  artistName: String,
                  url: String) extends DbEntry
