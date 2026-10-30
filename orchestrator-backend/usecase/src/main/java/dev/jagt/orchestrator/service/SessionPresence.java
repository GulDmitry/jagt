package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Reads the task's window into its session record, and answers from the record. */
@Service
@RequiredArgsConstructor
public class SessionPresence implements AgentPresence {

    private final StateService stateService;
    private final SessionProbe probe;
    private final OrchestratorProperties properties;
    private final Clock clock;

    @Override
    public boolean agentLive(String taskId) {
        TaskState task = stateService.task(taskId).orElseThrow(() -> Refusal.noSuchTask(taskId));
        long now = clock.millis();
        SessionSign.Read read;
        try {
            read = probe.read(taskId, task, properties.watchdog().staleAfter().toMillis(), now);
        } catch (SessionHost.WindowsUnreadable unread) {
            throw new Unread(String.valueOf(unread.getMessage()));
        }
        stateService.updateTask(taskId, current -> TaskReducer.signed(current, read, now));
        Boolean alive = stateService.task(taskId).map(current -> current.session().alive()).orElse(null);
        if (alive == null) {
            throw new Unread("the window was not read");
        }
        return alive;
    }
}
