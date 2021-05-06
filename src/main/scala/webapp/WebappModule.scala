package webapp

import com.google.inject.AbstractModule
import service.job.orchestration.OrchestrationMaster

class WebappModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[MainController]).asEagerSingleton()
    bind(classOf[OrchestrationMaster]).asEagerSingleton()
  }
}
