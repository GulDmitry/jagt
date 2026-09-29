package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.MasterAssistant.Answer;
import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TicketFacts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Whether a task's item has reached the stage that ends it. Closing deletes a worktree with nobody watching, so
 * every answer short of a clean yes leaves the task exactly where it was: an unreadable stage, a worktree
 * holding work nobody has committed, and a worktree git could not answer for are all reasons not to.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrackerClose {

    private final TicketReader tickets;
    private final TrackerWorkflow workflow;
    private final WorktreeChanges changes;

    public boolean closes(String taskId, TaskState task) {
        Answer<TicketFacts> read = tickets.read(taskId);
        tickets.charge(taskId, read.usage());
        Optional<TicketFacts> facts = read.facts().filter(TicketFacts::usable);
        if (facts.isEmpty() || facts.get().trackerStatus().isBlank()) {
            log.atWarn().setMessage("close stage unreadable")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", read.facts().isEmpty() ? "the read never reached the tracker"
                            : "the item came back with no workflow status")
                    .log();
            return false;
        }
        if (!workflow.closesWork(facts.get())) {
            return false;
        }
        return worktreeCanGo(taskId, task);
    }

    private boolean worktreeCanGo(String taskId, TaskState task) {
        Optional<Boolean> uncommitted = changes.uncommitted(task);
        if (uncommitted.isEmpty()) {
            log.atWarn().setMessage("close held back")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", "git could not say whether the worktree holds uncommitted work")
                    .log();
            return false;
        }
        if (uncommitted.get()) {
            log.atWarn().setMessage("close held back")
                    .addKeyValue("task", taskId)
                    .addKeyValue("cause", "the worktree holds work nobody has committed, and closing deletes it")
                    .log();
            return false;
        }
        return true;
    }
}
