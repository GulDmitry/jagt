package dev.jagt.orchestrator.port;

/**
 * How a job reports a failure it handled itself. The job counts as failed with that cause until its
 * next run begins; a run that books nothing ends clean.
 */
public interface JobFaults {

  /** Booked against the job running on this thread; on a thread no job runs on, nothing is. */
  void faulted(String cause);

  /** {@code work} for another thread, booked against the job running on this one. */
  Runnable carry(Runnable work);
}
