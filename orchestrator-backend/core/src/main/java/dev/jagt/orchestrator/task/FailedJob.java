package dev.jagt.orchestrator.task;

/** One job of a pipeline that failed, with the reason the host gives for it, blank when it gives none. */
public record FailedJob(String id, String name, String reason) {

    public FailedJob {
        reason = reason == null ? "" : reason.strip();
    }
}
