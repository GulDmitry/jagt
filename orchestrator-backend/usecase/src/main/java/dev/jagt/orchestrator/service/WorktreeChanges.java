package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Whether a task's worktrees hold uncommitted work. A round's own account of itself is a claim; this is the
 * measurement jagt can take instead of believing it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorktreeChanges {

    private final ConfigService configService;
    private final GitService gitService;

    /** ANY repository of the task, since one changed file anywhere is a diff for the human to read. */
    public boolean anyUncommitted(TaskState task) {
        return task.repos().stream().anyMatch(this::uncommitted);
    }

    /**
     * A worktree git cannot answer for is reported as CLEAN: this only ever qualifies what an agent said, and a
     * probe that fails must not turn its report into the opposite claim.
     */
    private boolean uncommitted(TaskRepo repo) {
        try {
            return gitService.hasUncommittedChanges(
                    Path.of(configService.project(repo.project()).path()).toAbsolutePath().normalize(),
                    Path.of(repo.worktreePath()));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("worktree read failed")
                    .addKeyValue("path", repo.worktreePath())
                    .addKeyValue("cause", e.toString())
                    .log();
            return false;
        }
    }
}
