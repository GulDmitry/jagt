package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.TicketTexts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/** Read while the session works, so the round it hands back starts with the ticket in every reviewer's prompt. */
@Service
@RequiredArgsConstructor
public class TicketPrefetchJob implements TaskJob {

    private final ConfigService configService;
    private final TicketTexts tickets;

    @Override
    public String id() {
        return "ticket-prefetch";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "read each working task's ticket once, for the Master's next review of it";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(5);
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStatuses(TaskStatus.IN_PROGRESS);
    }

    @Override
    public void run(String taskId, TaskState task) {
        if (configService.load().master().running() && due(taskId, task)) {
            tickets.read(taskId, Objects.requireNonNull(task.ticketUrl()), task.statusSince());
        }
    }

    private boolean due(String taskId, TaskState task) {
        return task.ticketUrl() != null && !task.ticketUrl().isBlank()
                && !tickets.readSince(taskId, task.statusSince());
    }
}
