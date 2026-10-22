package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final WorktreeInspection inspection;

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

    /** Every repository's {@link WorktreeInspection#treeState}; EMPTY where git could not answer for one. */
    public Optional<String> state(TaskState task) {
        StringBuilder state = new StringBuilder();
        for (TaskRepo repo : task.repos()) {
            try {
                state.append(inspection.treeState(projectPath(Objects.requireNonNull(repo.project())), Path.of(repo.worktreePath())))
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
            return Optional.of(inspection.hasUncommittedChanges(projectPath(Objects.requireNonNull(repo.project())),
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
            return inspection.hasUncommittedChanges(projectPath(project), worktree)
                    || inspection.aheadOfTarget(projectPath(project), worktree, targetBranch);
        } catch (RuntimeException e) {
            warn(worktreePath, e);
            return true;
        }
    }

    /** Whether a ship would carry anything: work some repository holds. An open request alone is not that. */
    public boolean anyToShip(TaskState task) {
        return task.repos().stream().anyMatch(repo -> holdsWork(Objects.requireNonNull(repo.project()),
                Objects.requireNonNull(repo.worktreePath()),
                targetBranch(task, configService.project(Objects.requireNonNull(repo.project())).baseBranch())));
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
                String base = task.baseBranchOr(configService.project(Objects.requireNonNull(repo.project())).baseBranch());
                diff.append(inspection.changesSince(projectPath(Objects.requireNonNull(repo.project())), Path.of(repo.worktreePath()), base));
            } catch (RuntimeException e) {
                warn(repo.worktreePath(), e);
                diff.append("jagt could not read it: ").append(e.getMessage()).append('\n');
            }
        }
        return diff.length() > MAX_QUOTED ? "" : diff.toString();
    }

    /** Each repository's changed paths since its base, by project; EMPTY where git could not answer for one. */
    public Optional<Map<String, List<String>>> paths(TaskState task) {
        Map<String, List<String>> paths = new LinkedHashMap<>();
        for (TaskRepo repo : task.repos()) {
            String project = Objects.requireNonNull(repo.project());
            try {
                String base = task.baseBranchOr(configService.project(project).baseBranch());
                paths.put(project, inspection.changedPaths(projectPath(project), Path.of(repo.worktreePath()), base));
            } catch (RuntimeException e) {
                warn(repo.worktreePath(), e);
                return Optional.empty();
            }
        }
        return Optional.of(paths);
    }

    /** Every repository's {@link WorktreeInspection#ownDiffId}; EMPTY where one cannot say. */
    public Optional<String> ownDiff(TaskState task) {
        StringBuilder id = new StringBuilder();
        for (TaskRepo repo : task.repos()) {
            String project = Objects.requireNonNull(repo.project());
            try {
                String base = task.baseBranchOr(configService.project(project).baseBranch());
                Optional<String> own = inspection.ownDiffId(projectPath(project), Path.of(repo.worktreePath()), base);
                if (own.isEmpty()) {
                    return Optional.empty();
                }
                id.append(own.get()).append(';');
            } catch (RuntimeException e) {
                warn(repo.worktreePath(), e);
                return Optional.empty();
            }
        }
        return Optional.of(id.toString());
    }

    /** Lines the task added to every repository's agent files; EMPTY where git could not count them for one. */
    public Optional<Integer> agentFileLinesAdded(TaskState task) {
        int added = 0;
        for (TaskRepo repo : task.repos()) {
            try {
                String base = task.baseBranchOr(configService.project(Objects.requireNonNull(repo.project())).baseBranch());
                added += inspection.agentFileLinesAdded(projectPath(Objects.requireNonNull(repo.project())), Path.of(repo.worktreePath()), base);
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

    private void warn(@Nullable String worktreePath, RuntimeException cause) {
        log.atWarn().setMessage("worktree read failed")
                .addKeyValue("path", worktreePath)
                .addKeyValue("cause", cause.toString())
                .log();
    }
}
