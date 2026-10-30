package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.AgentSessions;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** An agent exiting, or stopped at a prompt, before its harness loads reports nothing; only its window shows it. */
@Service
@RequiredArgsConstructor
public class LaunchExits implements TaskJob {

    private final AgentSessions sessions;
    private final WatchdogService watchdog;

    @Override
    public String id() {
        return "launch-exits";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "tell an agent that exited before its first report, with the last lines it printed, or waits at a prompt jagt never answers";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(5);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStatuses(TaskStatus.NEW);
    }

    @Override
    public void run(String taskId, TaskState task) {
        sessions.exitTail(taskId).ifPresentOrElse(printed -> watchdog.ended(taskId, printed),
                () -> sessions.unansweredPrompt(taskId).ifPresent(cause -> watchdog.waiting(taskId, cause)));
    }
}
