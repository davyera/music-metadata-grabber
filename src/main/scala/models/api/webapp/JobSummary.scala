package models.api.webapp

case class JobSummary(_id: String,
                      service_name: String,
                      job_name: String,
                      completed: Boolean,
                      failed: Boolean,
                      failure_message: String,
                      running_time_ms: Long)
