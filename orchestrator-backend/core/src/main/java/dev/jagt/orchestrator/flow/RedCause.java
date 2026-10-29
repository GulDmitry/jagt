package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.FailedJob;

import java.util.List;
import java.util.Set;

/** Whose fault a red pipeline is, which decides whether the session hears of it. */
public enum RedCause {

    /** Anything not known to be infrastructure, a reason the host gives for none included. */
    CODE,
    /** Every failed job died on the runner or the host, so no change to the tree can mend it. */
    INFRASTRUCTURE;

    private static final Set<String> INFRASTRUCTURE_REASONS = Set.of("runner_system_failure",
            "stuck_or_timeout_failure", "api_failure", "scheduler_failure", "data_integrity_failure",
            "startup_failure");

    public static RedCause of(List<FailedJob> failed) {
        return !failed.isEmpty() && failed.stream().allMatch(job -> INFRASTRUCTURE_REASONS.contains(job.reason()))
                ? INFRASTRUCTURE : CODE;
    }
}
