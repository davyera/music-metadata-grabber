package models

trait PageableWithTotal {
  def getTotal: Int
}

trait PageableWithNext {
  def getNextPage: Option[Int]
}
