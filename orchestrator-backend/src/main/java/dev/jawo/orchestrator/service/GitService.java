package dev.jawo.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * All Git operations against a base repository are serialized with a
 * per-repository ReentrantLock to avoid .git/index.lock races between
 * concurrent agents. Different repositories don't contend: a slow
 * `git fetch` in one project must not block initializing tasks in another.
 */
@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);
    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(3);

    private final ConcurrentHashMap<String, ReentrantLock> repoLocks = new ConcurrentHashMap<>();
    private final ProcessRunner processRunner;

    public GitService(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    public enum BranchStrategy { FRESH, RECREATE, RESUME }

    public void createWorktree(Path projectPath, Path worktreePath, String branch, String baseBranch,
                               BranchStrategy strategy) {
        withRepoLock(projectPath, () -> {
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            boolean branchExists = processRunner.run(projectPath, GIT_TIMEOUT,
                    List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/" + branch)).exitCode() == 0;
            if (branchExists) {
                switch (strategy) {
                    // A reopened ticket after a squash merge looks "unmerged" to git, an
                    // aborted task may hold unpushed work — deleting silently is never safe.
                    case FRESH -> throw new IllegalArgumentException("Branch '" + branch
                            + "' already exists (previous run of this ticket). Decide what to do and retry with"
                            + " branchStrategy: 'recreate' (old work merged/obsolete -> delete branch, start fresh"
                            + " from " + baseBranch + ") or 'resume' (continue the existing branch and its commits).");
                    case RECREATE -> processRunner.run(projectPath, GIT_TIMEOUT,
                                    List.of("git", "branch", "-D", branch))
                            .expectSuccess("git branch -D " + branch);
                    case RESUME -> {
                        processRunner.run(projectPath, GIT_TIMEOUT,
                                        List.of("git", "worktree", "add", worktreePath.toString(), branch))
                                .expectSuccess("git worktree add (resume) " + worktreePath);
                        detachUpstream(projectPath, branch);
                        return;
                    }
                }
            }
            processRunner.run(projectPath, GIT_TIMEOUT,
                            List.of("git", "worktree", "add", "-b", branch, worktreePath.toString(), baseBranch))
                    .expectSuccess("git worktree add " + worktreePath);
            detachUpstream(projectPath, branch);
        });
    }

    /**
     * CRITICAL SAFETY: a branch created from origin/release/sng inherits it as
     * upstream, so a bare `git push` from an agent would target the RELEASE
     * branch. Unset the upstream — now a bare push errors ("no upstream"), and
     * the agent must push explicitly to its own branch.
     */
    private void detachUpstream(Path projectPath, String branch) {
        processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "branch", "--unset-upstream", branch));
    }

    /**
     * Removes a worktree; when branchToDelete is non-null the branch goes too
     * (compensation for a failed initialize_task, where the branch has no
     * commits of its own yet). Best-effort: failures are logged, not thrown.
     */
    public void removeWorktree(Path projectPath, Path worktreePath, String branchToDelete) {
        withRepoLock(projectPath, () -> {
            var removed = processRunner.run(projectPath, GIT_TIMEOUT,
                    List.of("git", "worktree", "remove", "--force", worktreePath.toString()));
            if (removed.exitCode() != 0) {
                log.warn("git worktree remove {} failed: {}", worktreePath, removed.stderr());
                return;
            }
            // An open IDE (`.idea/`) or editor may drop metadata into the dir around
            // removal — git leaves such remnants behind; finish the job.
            if (Files.exists(worktreePath)) {
                try (var paths = Files.walk(worktreePath)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                } catch (IOException e) {
                    log.warn("Could not delete worktree remnants at {}: {}", worktreePath, e.getMessage());
                }
            }
            if (branchToDelete != null) {
                var branch = processRunner.run(projectPath, GIT_TIMEOUT,
                        List.of("git", "branch", "-D", branchToDelete));
                if (branch.exitCode() != 0) {
                    log.warn("git branch -D {} failed: {}", branchToDelete, branch.stderr());
                }
            }
        });
    }

    /**
     * Merges sourceBranch into targetBranch and pushes — via a throwaway worktree,
     * because the target branch may not be checked out anywhere. On conflict the
     * merge is aborted cleanly and the human resolves it manually.
     */
    public void mergeIntoAndPush(Path projectPath, String sourceBranch, String targetBranch) {
        withRepoLock(projectPath, () -> {
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            Path temp;
            try {
                temp = Files.createTempDirectory("jawo-deploy-");
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create temp dir for deploy worktree", e);
            }
            String tempBranch = "jawo-deploy-" + sourceBranch;
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "add",
                            "-B", tempBranch, temp.toString(), "origin/" + targetBranch))
                    .expectSuccess("git worktree add (deploy) " + targetBranch);
            try {
                // Explicit message: the merge runs on a temp branch (jawo-deploy-*), and git's
                // default "into <current branch>" would leak that name instead of the real target.
                var merge = processRunner.run(temp, GIT_TIMEOUT, List.of("git", "merge", "--no-edit",
                        "-m", "Merge branch '" + sourceBranch + "' into " + targetBranch, sourceBranch));
                if (merge.exitCode() != 0) {
                    processRunner.run(temp, GIT_TIMEOUT, List.of("git", "merge", "--abort"));
                    throw new IllegalStateException("Merge CONFLICT merging " + sourceBranch + " into "
                            + targetBranch + " — nothing was pushed. Resolve manually and retry. Details: "
                            + (merge.stderr().isBlank() ? merge.stdout() : merge.stderr()));
                }
                processRunner.run(temp, GIT_TIMEOUT, List.of("git", "push", "origin", "HEAD:" + targetBranch))
                        .expectSuccess("git push origin HEAD:" + targetBranch);
            } finally {
                processRunner.run(projectPath, GIT_TIMEOUT,
                        List.of("git", "worktree", "remove", "--force", temp.toString()));
                processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "branch", "-D", tempBranch));
            }
        });
    }

    /**
     * A throwaway detached worktree at the base branch, for `idea diff <base> <task worktree>`
     * (shows the task's changes as a directory compare). Reused per task: the previous one is
     * removed first, so diffs don't accumulate. Returns the checkout path.
     */
    public Path checkoutBaseForDiff(Path projectPath, String baseBranch, String taskId) {
        return withRepoLock(projectPath, () -> {
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            Path temp = Path.of(System.getProperty("java.io.tmpdir"), "jawo-diff-" + taskId);
            processRunner.run(projectPath, GIT_TIMEOUT,
                    List.of("git", "worktree", "remove", "--force", temp.toString()));
            processRunner.run(projectPath, GIT_TIMEOUT,
                            List.of("git", "worktree", "add", "--detach", temp.toString(), baseBranch))
                    .expectSuccess("git worktree add (diff base) " + temp);
            return temp;
        });
    }

    /**
     * The origin remote URL identifies the project on any Git host (GitLab, GitHub, ...) —
     * agents derive the API project path from it instead of hardcoded ids in config.
     */
    public String remoteUrl(Path projectPath) {
        return withRepoLock(projectPath, () ->
                processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "remote", "get-url", "origin"))
                        .expectSuccess("git remote get-url origin in " + projectPath)
                        .stdout()
                        .trim());
    }

    /**
     * Resolves the shared .git directory of the repository so orchestrator files
     * (symlinks, CLAUDE.md, task_context.md) can be added to info/exclude and never
     * pollute git status in any worktree.
     */
    public Path gitCommonDir(Path projectPath) {
        return withRepoLock(projectPath, () -> {
            String dir = processRunner.run(projectPath, GIT_TIMEOUT,
                            List.of("git", "rev-parse", "--path-format=absolute", "--git-common-dir"))
                    .expectSuccess("git rev-parse --git-common-dir")
                    .stdout();
            return Path.of(dir.trim());
        });
    }

    private void withRepoLock(Path projectPath, Runnable action) {
        withRepoLock(projectPath, () -> {
            action.run();
            return null;
        });
    }

    private <T> T withRepoLock(Path projectPath, java.util.function.Supplier<T> action) {
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
