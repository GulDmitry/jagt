package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.task.TaskStop;
import org.jspecify.annotations.Nullable;

/** The stop region's states: none, or the way the standing stop ends. */
enum StopKind {
    NONE, LIFT, HOST_READ, SESSION_ALIVE, SESSION_REPORT;

    static StopKind of(@Nullable TaskStop stop) {
        return stop == null ? NONE : of(stop.trigger().ends());
    }

    static StopKind of(TaskStop.Trigger.Ends ends) {
        return valueOf(ends.name());
    }
}
