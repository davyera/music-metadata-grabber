package models

import service.job.orchestration.JobRecurrence.JobRecurrence
import service.job.orchestration.OrchestrationType.OrchestrationType

case class OrchestrationSummary(_id: String,
                                orchestration_type: OrchestrationType,
                                parameter: String,
                                scheduled_for: String,
                                recurrence: JobRecurrence)