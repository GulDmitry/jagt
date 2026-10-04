package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What jagt reads for a round once, so the roles judging it quote it rather than each read it again. */
@Component
@RequiredArgsConstructor
public class RoundFacts {

    private final TicketTexts tickets;
    private final WorktreeChanges changes;

    public String ticket(String taskId) {
        return tickets.of(taskId).orElse("");
    }

    public String diff(TaskState task) {
        return changes.diff(task);
    }
}
