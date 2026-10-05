package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** What jagt reads for a round once, so the roles judging it quote it rather than each read it again. */
@Component
@RequiredArgsConstructor
public class RoundFacts {

    private final TicketTexts tickets;
    private final WorktreeChanges changes;
    private final AgentRuntime agentRuntime;

    public String ticket(String taskId) {
        return tickets.of(taskId).orElse("");
    }

    public String diff(TaskState task) {
        return changes.diff(task);
    }

    /** Empty where the session's record could not be read. */
    public Optional<List<String>> humanSaid(TaskState task) {
        return agentRuntime.humanSaid(Path.of(task.worktreePath()), Set.of(AgentSessions.NUDGE));
    }
}
