package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Looks for the window of every task stopped for want of one, so the stop can end once it runs again. */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionAliveJob implements TaskJob {

    private final AgentPresence agents;
    private final TaskStops stops;

    @Override
    public String id() {
        return "session-alive";
    }

    @Override
    public String describe() {
        return "look for the window of a task stopped because a step never landed, and end the stop once it runs";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStops(StopKind.SESSION_ALIVE);
    }

    @Override
    public void run(String taskId, TaskState task) {
        look(taskId);
    }

    private void look(String taskId) {
        try {
            if (agents.agentLive(taskId)) {
                stops.endOnSessionAlive(taskId).ifPresent(stop -> log.atInfo().setMessage("stop voided by read")
                        .addKeyValue("task", taskId).addKeyValue("trigger", stop.trigger())
                        .addKeyValue("read", "session alive").log());
            }
        } catch (RuntimeException e) {
            log.atWarn().setMessage("session read failed").addKeyValue("task", taskId)
                    .addKeyValue("cause", e.getMessage()).addKeyValue("effect", "look again in a minute").log();
        }
    }
}
