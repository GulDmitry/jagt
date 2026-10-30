package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.task.TaskState;

import java.util.Map;

/** A sign of life that arrives with every call is written at most once per throttle window. */
public final class KeepAlive {

    static final long THROTTLE_MS = 15_000;

    private KeepAlive() {
    }

    public static void beat(StateService stateService, Map.Entry<String, TaskState> task, SessionSign sign, long now) {
        if (now - task.getValue().lastActiveTimestamp() > THROTTLE_MS) {
            stateService.updateTask(task.getKey(), held -> TaskReducer.signed(held.touched(now), sign, now));
        }
    }
}
