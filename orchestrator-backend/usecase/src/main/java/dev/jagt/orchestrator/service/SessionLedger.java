package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.flow.TaskReducer;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Clock;

/** Writes what a session's harness reported: into the probe's memory, and as a sign on the task. */
@Component
@RequiredArgsConstructor
public class SessionLedger {

    private final SessionProbe probe;
    private final StateService stateService;
    private final Clock clock;

    public void record(String taskId, SessionProbe.State state, @Nullable String cause, @Nullable Path sessionLog,
                       SessionSign sign) {
        if (sessionLog != null) {
            probe.logAt(taskId, sessionLog);
        }
        long now = clock.millis();
        probe.report(taskId, state, cause, now);
        stateService.updateTask(taskId, task -> TaskReducer.signed(task, sign, now));
    }
}
