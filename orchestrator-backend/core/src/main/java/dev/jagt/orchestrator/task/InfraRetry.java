package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Optional;

/**
 * A re-run of a pipeline that died on infrastructure: due at {@code at} while not {@code sent}, the moment it was
 * sent after. {@code jobs} and {@code reason} are what the host said failed.
 */
public record InfraRetry(long at, String pipelineUrl, String jobs, String reason, boolean sent) {

    public InfraRetry {
        pipelineUrl = pipelineUrl == null ? "" : pipelineUrl;
        jobs = jobs == null ? "" : jobs;
        reason = reason == null ? "" : reason;
    }

    public static InfraRetry armed(long at, String pipelineUrl, String jobs, String reason) {
        return new InfraRetry(at, pipelineUrl, jobs, reason, false);
    }

    public InfraRetry sentAt(long now) {
        return new InfraRetry(now, pipelineUrl, jobs, reason, true);
    }

    /** The stop once {@code limit} retries were sent since the last lift and the checks are red again. */
    public static Optional<TaskStop> stop(List<InfraRetry> retries, RoundTrail trail, int limit, long now) {
        List<InfraRetry> sinceLift = retries.stream().filter(retry -> retry.sent() && retry.at() > trail.since()).toList();
        if (limit <= 0 || sinceLift.size() < limit) {
            return Optional.empty();
        }
        InfraRetry last = sinceLift.getLast();
        return Optional.of(new TaskStop(TaskStop.Trigger.INFRA, now,
                last.jobs() + ": " + last.reason() + ", " + sinceLift.size() + " retries"));
    }
}
