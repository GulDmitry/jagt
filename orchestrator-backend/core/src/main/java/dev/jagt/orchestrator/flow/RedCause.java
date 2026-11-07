package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.FailedJob;
import java.util.List;

/** Whose fault a red pipeline is, which decides whether the session hears of it. */
public enum RedCause {

  /** Anything not known to be infrastructure, a reason the host gives for none included. */
  CODE,
  /** Every failed job died on the runner or the host, so no change to the tree can mend it. */
  INFRASTRUCTURE;

  public static RedCause of(List<FailedJob> failed) {
    return !failed.isEmpty() && failed.stream().allMatch(FailedJob::infrastructure)
        ? INFRASTRUCTURE
        : CODE;
  }
}
