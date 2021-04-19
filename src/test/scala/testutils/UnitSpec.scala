package testutils

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.scalatest.Matchers.fail
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

object UnitSpec {}

trait UnitSpec extends FlatSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfter {
  implicit val context: ExecutionContext = ExecutionContext.Implicits.global
  implicit val patience: PatienceConfig = PatienceConfig(Span(2, Seconds), Span(0.5, Seconds))

  protected def getLogVerifier[T](forClass: Class[T]): LogVerifier = {
    val logger = LoggerFactory.getLogger(forClass).asInstanceOf[Logger]
    new LogVerifier(logger)
  }

  def assertLogged(appender: ListAppender[ILoggingEvent], logIndex: Int, msg: String): Unit = {
    assert(appender.list.get(logIndex).getMessage.equals(msg))
  }
}

class LogVerifier(private val logger: Logger) {
  private val listAppender = new ListAppender[ILoggingEvent]
  listAppender.start()
  logger.addAppender(listAppender)

  /** Assert msg was logged at specific log index */
  def assertLogged(logIndex: Int, msg: String): Unit =
    assert(listAppender.list.get(logIndex).getFormattedMessage.equals(msg))

  /** Assert message was logged at all (ignore index) */
  def assertLogged(msg: String): Unit = {
    listAppender.list.forEach { logged =>
      if (logged.getFormattedMessage.equals(msg))
        return
    }
    fail(s"Expected log: $msg")
  }

  def assertLogCount(count: Int): Unit =
    assert(listAppender.list.size().equals(count))
}