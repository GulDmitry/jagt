package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** A fresh session held back for the human's unsent input or open exchange starts once they are seen gone twice running. */
@Service
@RequiredArgsConstructor
public class FreshStartWaitJob implements TaskJob {

    private final AgentSessions sessions;

    @Override
    public String id() {
        return "fresh-start-wait";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "start the fresh session held back while the human types in or answers inside the agent's window";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(2);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStops(StopKind.withoutPause());
    }

    @Override
    public void run(String taskId, TaskState task) {
        sessions.startWaitingFreshWhenFree(taskId);
    }
}
