package service

import java.util.TimerTask
import java.util.concurrent.ScheduledThreadPoolExecutor
import scala.concurrent.duration.TimeUnit
import scala.jdk.CollectionConverters._

object ScheduledTasks {
  private val tasks = new java.util.concurrent.ConcurrentHashMap[SimpleScheduledTask, Unit]()
  def add(task: SimpleScheduledTask): Unit = tasks.put(task, ())
  def killTasks(): Unit = tasks.keys.asScala.toSeq.foreach(_.kill())
}

case class SimpleScheduledTask(interval: Int,
                               unit: TimeUnit,
                               work: () => Unit) {
  private val executor = new ScheduledThreadPoolExecutor(1)
  private val task = new TimerTask {
    override def run(): Unit = work()
  }
  executor.scheduleAtFixedRate(task, interval, interval, unit)
  ScheduledTasks.add(this)

  private[service] def kill(): Unit = executor.shutdown()
}
