package service

import com.typesafe.scalalogging.StrictLogging

// TODO
class DataReceiver extends StrictLogging {
  def receive[T](payload: T): Unit = {
//    logger.info(s"Received Data: $payload")
  }
}
