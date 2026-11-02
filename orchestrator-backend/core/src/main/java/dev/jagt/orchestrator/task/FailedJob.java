package dev.jagt.orchestrator.task;

/**
 * One job of a pipeline that failed, with the reason the host gives for it, blank when it gives none.
 * {@code infrastructure} is the driver's verdict that the job died on the runner or the host, never on the code.
 */
public record FailedJob(String id, String name, String reason, boolean infrastructure) {

    public FailedJob {
        reason = reason == null ? "" : reason.strip();
    }
}
