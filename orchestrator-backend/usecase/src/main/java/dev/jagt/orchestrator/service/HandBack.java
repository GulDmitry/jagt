package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What the worktree says about a round being handed back, which is what a report cannot be believed about. */
@Service
@RequiredArgsConstructor
public class HandBack {

    private final WorktreeChanges worktreeChanges;
    private final Rounds rounds;
    private final ConfigService configService;

    public boolean anyUncommitted(TaskState task) {
        return worktreeChanges.anyUncommitted(task);
    }

    public RoundState round(TaskState task) {
        return rounds.of(task);
    }

    /** Whether this hand-back still owes jagt a verification run, which holds it at VERIFYING. */
    public boolean verificationOwed(TaskState task) {
        return task.repos().stream()
                .anyMatch(repo -> !Verification.command(configService.project(repo.project())).isEmpty());
    }

    public boolean masterReads() {
        return configService.load().master().running();
    }
}
