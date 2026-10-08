package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.WorktreeProcesses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Serialized per repository: index.lock races are per-repository, and a slow fetch must not block another. */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitCommands {

    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(3);

    private final ConcurrentHashMap<String, ReentrantLock> repoLocks = new ConcurrentHashMap<>();
    private final Processes processRunner;
    private final WorktreeProcesses worktreeProcesses;

    Processes.Result run(Path workingDir, List<String> command) {
        return processRunner.run(workingDir, GIT_TIMEOUT, command);
    }

    Processes.Result run(Path workingDir, Map<String, String> env, List<String> command) {
        return processRunner.run(workingDir, GIT_TIMEOUT, env, command);
    }

    void reap(Path worktree) {
        worktreeProcesses.reap(worktree);
    }

    /** The repository a checkout belongs to, empty when it is not a checkout at all. */
    Optional<Path> worktreeOwner(Path worktree) {
        // Without its own `.git`, git would answer for whatever repository encloses the directory.
        if (!Files.isDirectory(worktree) || !Files.exists(worktree.resolve(".git"))) {
            return Optional.empty();
        }
        var gitDir = processRunner.run(worktree, GIT_TIMEOUT,
                List.of("git", "rev-parse", "--git-common-dir"));
        if (gitDir.exitCode() != 0) {
            return Optional.empty();
        }
        // Answered relative to the checkout in the main repository, absolute from a linked worktree.
        return Optional.ofNullable(worktree.resolve(gitDir.stdout().trim()).normalize().getParent());
    }

    /** Symlinked temp and home directories are the norm, and git answers with the path they resolve to. */
    static boolean sameDirectory(Path one, Path other) {
        try {
            return Files.isSameFile(one, other);
        } catch (IOException e) {
            return one.toAbsolutePath().normalize().equals(other.toAbsolutePath().normalize());
        }
    }

    /** A path a prior run left behind makes `git worktree add` fail, so registration and the directory both go. */
    void clearWorktreePath(Path projectPath, Path temp) {
        processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "remove", "--force", temp.toString()));
        processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "prune"));
        forceDeleteDir(temp);
    }

    /** Best-effort: the checkout and its branch are scaffolding, not state. */
    void removeWorktreeAndBranch(Path projectPath, Path worktree, String branch) {
        run(projectPath, List.of("git", "worktree", "remove", "--force", worktree.toString()));
        run(projectPath, List.of("git", "worktree", "prune"));
        run(projectPath, List.of("git", "branch", "-D", branch));
    }

    /** A process rooted in the directory can keep recreating files, so every pass kills it before deleting. */
    void forceDeleteDir(Path dir) {
        for (int attempt = 0; attempt < 4 && Files.exists(dir); attempt++) {
            worktreeProcesses.reap(dir);
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                log.atWarn().setMessage("directory delete failed")
                        .addKeyValue("path", dir)
                        .addKeyValue("attempt", attempt + 1)
                        .addKeyValue("cause", e.toString())
                        .log();
            }
        }
        if (Files.exists(dir)) {
            log.atWarn().setMessage("directory still present after delete")
                    .addKeyValue("path", dir)
                    .addKeyValue("cause", "a live process repopulates it")
                    .log();
        }
    }

    void locked(Path projectPath, Runnable action) {
        locked(projectPath, () -> {
            action.run();
            return null;
        });
    }

    <T> T locked(Path projectPath, Supplier<T> action) {
        ReentrantLock lock = repoLocks.computeIfAbsent(
                projectPath.toAbsolutePath().normalize().toString(), k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
