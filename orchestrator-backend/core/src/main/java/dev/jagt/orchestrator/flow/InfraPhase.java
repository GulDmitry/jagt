package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.TaskState;

import java.util.List;

/** The infra region's states: no re-run recorded, one armed, some sent and none armed, or the last wait sent. */
public enum InfraPhase {
    NONE, ARMED, SENT, SPENT;

    public static InfraPhase of(TaskState task) {
        List<InfraRetry> retries = task.infraRetries();
        if (InfraRetry.pending(retries).isPresent()) {
            return ARMED;
        }
        if (retries.isEmpty()) {
            return NONE;
        }
        InfraRetry last = retries.getLast();
        return last.attempt() >= last.of() ? SPENT : SENT;
    }
}
