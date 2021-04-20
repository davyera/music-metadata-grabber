package models

import models.db.{Album, Artist, Track}

case class ArtistSummary(artist: Artist, albums: Seq[Album], tracks: Seq[Track])

