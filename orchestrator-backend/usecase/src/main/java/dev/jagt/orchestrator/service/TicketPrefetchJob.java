package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Read while the session works, so the round it hands back starts with the ticket in every reviewer's prompt. */
@Service
@RequiredArgsConstructor
public class TicketPrefetchJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final TicketTexts tickets;

    @Override
    public String id() {
        return "ticket-prefetch";
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
    public void run() {
        if (!configService.load().master().running()) {
            return;
        }
        stateService.tasks().forEach((taskId, task) -> {
            if (due(taskId, task)) {
                tickets.read(taskId, task.ticketUrl(), task.statusSince());
            }
        });
    }

    private boolean due(String taskId, TaskState task) {
        return task.status() == TaskStatus.IN_PROGRESS && task.ticketUrl() != null && !task.ticketUrl().isBlank()
                && !tickets.readSince(taskId, task.statusSince());
    }
}
