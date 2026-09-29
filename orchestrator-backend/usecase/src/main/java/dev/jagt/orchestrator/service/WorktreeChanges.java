package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

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

    /**
     * ANY repository of the task, since one changed file anywhere is a diff for the human to read. A worktree
     * git cannot answer for counts as CLEAN here: a failed probe must not invert an agent's own report.
     */
    public boolean anyUncommitted(TaskState task) {
        return uncommitted(task).orElse(false);
    }

    /**
     * The same measurement for a caller that may not act on ignorance: EMPTY where git could not answer for
     * some worktree, which is a third answer and never the same as finding nothing there.
     */
    public Optional<Boolean> uncommitted(TaskState task) {
        boolean anyChanged = false;
        for (TaskRepo repo : task.repos()) {
            Optional<Boolean> read = uncommitted(repo);
            if (read.isEmpty()) {
                return Optional.empty();
            }
            anyChanged |= read.get();
        }
        return Optional.of(anyChanged);
    }

    private Optional<Boolean> uncommitted(TaskRepo repo) {
        try {
            return Optional.of(gitService.hasUncommittedChanges(
                    Path.of(configService.project(repo.project()).path()).toAbsolutePath().normalize(),
                    Path.of(repo.worktreePath())));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("worktree read failed")
                    .addKeyValue("path", repo.worktreePath())
                    .addKeyValue("cause", e.toString())
                    .log();
            return Optional.empty();
        }
    }
}
