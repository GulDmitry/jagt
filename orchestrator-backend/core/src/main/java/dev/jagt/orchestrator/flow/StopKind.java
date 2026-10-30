package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStop;
import org.jspecify.annotations.Nullable;

/** The stop region's states: none, or the way the standing stop ends. */
public enum StopKind {
    NONE, LIFT, HOST_READ, SESSION_ALIVE, SESSION_REPORT, DEPLOY_READ;

    public static StopKind of(@Nullable TaskStop stop) {
        return stop == null ? NONE : of(stop.trigger().ends());
    }

    public static StopKind of(TaskStop.Trigger.Ends ends) {
        return valueOf(ends.name());
    }
}
