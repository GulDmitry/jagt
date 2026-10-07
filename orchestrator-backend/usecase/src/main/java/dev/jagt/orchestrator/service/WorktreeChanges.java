package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Whether a task's worktrees hold work. A round's own account of itself is a claim; this is the measurement jagt
 * can take instead of believing it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorktreeChanges {

    private static final int MAX_QUOTED = 100_000;

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

    /** Every repository's {@link GitService#treeState}; EMPTY where git could not answer for one. */
    public Optional<String> state(TaskState task) {
        StringBuilder state = new StringBuilder();
        for (TaskRepo repo : task.repos()) {
            try {
                state.append(gitService.treeState(projectPath(repo.project()), Path.of(repo.worktreePath())))
                        .append(';');
            } catch (RuntimeException e) {
                warn(repo.worktreePath(), e);
                return Optional.empty();
            }
        }
        return Optional.of(state.toString());
    }

    private Optional<Boolean> uncommitted(TaskRepo repo) {
        try {
            return Optional.of(gitService.hasUncommittedChanges(projectPath(repo.project()),
                    Path.of(repo.worktreePath())));
        } catch (RuntimeException e) {
            warn(repo.worktreePath(), e);
            return Optional.empty();
        }
    }

    /**
     * Whether this repository holds anything a ship could carry: work in the worktree, or commits its target does
     * not hold. A worktree git cannot answer for counts as HOLDING work — one passed over on ignorance is a
     * half-shipped task.
     */
    public boolean holdsWork(String project, String worktreePath, String targetBranch) {
        Path worktree = Path.of(worktreePath);
        try {
            return gitService.hasUncommittedChanges(projectPath(project), worktree)
                    || gitService.aheadOfTarget(projectPath(project), worktree, targetBranch);
        } catch (RuntimeException e) {
            warn(worktreePath, e);
            return true;
        }
    }

    /** Whether a ship would carry anything: a request already open, or work some repository holds. */
    public boolean anyToShip(TaskState task) {
        return task.repos().stream().anyMatch(repo -> repo.hasReviewRequest()
                || holdsWork(repo.project(), repo.worktreePath(),
                        targetBranch(task, configService.project(repo.project()).baseBranch())));
    }

    /** A task cut from a parent feature branch merges back into it, not into that repository's configured base. */
    public static String targetBranch(TaskState task, String configuredBase) {
        String base = task.baseBranchOr(configuredBase);
        return base == null ? "" : base.replaceFirst("^origin/", "");
    }

    /** Each repository's changes since its base, or why they could not be read; blank past what a prompt quotes. */
    public String diff(TaskState task) {
        StringBuilder diff = new StringBuilder();
        for (TaskRepo repo : task.repos()) {
            diff.append("# ").append(repo.project()).append('\n');
            try {
                String base = task.baseBranchOr(configService.project(repo.project()).baseBranch());
                diff.append(gitService.changesSince(projectPath(repo.project()), Path.of(repo.worktreePath()), base));
            } catch (RuntimeException e) {
                warn(repo.worktreePath(), e);
                diff.append("jagt could not read it: ").append(e.getMessage()).append('\n');
            }
        }
        return diff.length() > MAX_QUOTED ? "" : diff.toString();
    }

    /** Lines the task added to every repository's agent files; EMPTY where git could not count them for one. */
    public Optional<Integer> agentFileLinesAdded(TaskState task) {
        int added = 0;
        for (TaskRepo repo : task.repos()) {
            try {
                String base = task.baseBranchOr(configService.project(repo.project()).baseBranch());
                added += gitService.agentFileLinesAdded(projectPath(repo.project()), Path.of(repo.worktreePath()), base);
            } catch (RuntimeException e) {
                warn(repo.worktreePath(), e);
                return Optional.empty();
            }
        }
        return Optional.of(added);
    }

    private Path projectPath(String project) {
        return Path.of(configService.project(project).path()).toAbsolutePath().normalize();
    }

    private void warn(String worktreePath, RuntimeException cause) {
        log.atWarn().setMessage("worktree read failed")
                .addKeyValue("path", worktreePath)
                .addKeyValue("cause", cause.toString())
                .log();
    }
}
