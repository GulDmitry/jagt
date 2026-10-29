package dev.jagt.orchestrator.task;

import java.util.List;
import java.util.Optional;

/**
 * A re-run of a pipeline that died on infrastructure: due at {@code at} while not {@code sent}, the moment it was
 * sent after. {@code jobs} and {@code reason} are what the host said failed; it is retry {@code attempt} of {@code of}.
 */
public record InfraRetry(long at, String pipelineUrl, String jobs, String reason, boolean sent, int attempt, int of) {

    public InfraRetry {
        pipelineUrl = pipelineUrl == null ? "" : pipelineUrl;
        jobs = jobs == null ? "" : jobs;
        reason = reason == null ? "" : reason;
    }

    public static InfraRetry armed(long at, String pipelineUrl, String jobs, String reason, int attempt, int of) {
        return new InfraRetry(at, pipelineUrl, jobs, reason, false, attempt, of);
    }

    /** The one retry still waiting for its time. */
    public static Optional<InfraRetry> pending(List<InfraRetry> retries) {
        return retries.stream().filter(retry -> !retry.sent()).findFirst();
    }

    /** What is waiting and when: the host's reason, the jobs, and which retry this is. */
    public String reading() {
        return reason + ": " + jobs + " · retry " + attempt + "/" + of + " at "
                + UtcTime.clock(at);
    }

    public InfraRetry sentAt(long now) {
        return new InfraRetry(now, pipelineUrl, jobs, reason, true, attempt, of);
    }

    /** The retries sent after the last lift, oldest first. */
    public static List<InfraRetry> sentSince(List<InfraRetry> retries, RoundTrail trail) {
        return retries.stream().filter(retry -> retry.sent() && retry.at() > trail.since()).toList();
    }

    /** The stop once {@code limit} retries were sent since the last lift and the checks are red again. */
    public static Optional<TaskStop> stop(List<InfraRetry> retries, RoundTrail trail, int limit, long now) {
        List<InfraRetry> sinceLift = sentSince(retries, trail);
        if (limit <= 0 || sinceLift.size() < limit) {
            return Optional.empty();
        }
        InfraRetry last = sinceLift.getLast();
        return Optional.of(new TaskStop(TaskStop.Trigger.INFRA, now,
                last.jobs() + ": " + last.reason() + ", " + sinceLift.size() + " retries"));
    }
}
