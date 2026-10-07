package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.Specs;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

/** What the worktree says about a round being handed back, which is what a report cannot be believed about. */
@Service
@RequiredArgsConstructor
public class HandBack {

    private final WorktreeChanges worktreeChanges;
    private final ReviewDrafts reviewDrafts;
    private final Verification verification;
    private final ConfigService configService;
    private final Specs specs;

    public boolean anyUncommitted(TaskState task) {
        return worktreeChanges.anyUncommitted(task);
    }

    public boolean draftsPending(TaskState task, TaskStatus status) {
        return reviewDrafts.pending(task, status);
    }

    /** Whether this hand-back still owes jagt a verification run, which holds it at VERIFYING. */
    public boolean verificationOwed(TaskState task) {
        return verification.configured(task);
    }

    /** Why a session may not hand this round back yet, which only the worktree can say. */
    public Optional<String> notesOwed(TaskState task) {
        Path worktree = Path.of(task.worktreePath());
        return TaskNotes.owed(worktree).or(() -> worktreeChanges.agentFileLinesAdded(task)
                .map(added -> TaskNotes.agentFileOwed(worktree, added))
                .orElse(Optional.of("jagt could not count the lines this task added to the project's agent file")));
    }

    /** Why the change this round makes to the specs a repository keeps may not be handed back yet. */
    public Optional<String> specsOwed(String taskId, TaskState task) {
        return task.repos().stream()
                .map(repo -> specs.owed(Path.of(repo.worktreePath()), taskId).map(owed -> "[" + repo.project()
                        + "] " + owed))
                .flatMap(Optional::stream).findFirst();
    }

    public boolean masterReads() {
        return configService.load().master().running();
    }
}
