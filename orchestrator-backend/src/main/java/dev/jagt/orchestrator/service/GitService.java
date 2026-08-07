package dev.jagt.orchestrator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
        // Always cut from the REMOTE-TRACKING ref, never a local branch: `git fetch` below refreshes
        // origin/<base> but never fast-forwards a checkout-less local branch, so cutting from a local
        // `main` would inherit STALE history. Normalizing here (not trusting the config to spell it
        // `origin/...`) guarantees the subtree is always based on freshly fetched upstream — matching
        // what deploy already hardcodes for its target branch (see mergeIntoAndPush).
        String base = "origin/" + baseBranch.replaceFirst("^origin/", "");
        withRepoLock(projectPath, () -> {
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            // Self-heal: a previous `done` may have unregistered the worktree but failed to delete the
            // directory (a file held open), which makes `git worktree add` below fail "already exists".
            // Clear a stale leftover at the target path before creating.
            if (Files.exists(worktreePath)) {
                log.warn("Clearing a stale leftover worktree directory before creating {}", worktreePath);
                clearWorktreePath(projectPath, worktreePath);
            }
            boolean branchExists = processRunner.run(projectPath, GIT_TIMEOUT,
                    List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/" + branch)).exitCode() == 0;
            if (branchExists) {
                switch (strategy) {
                    // A reopened ticket after a squash merge looks "unmerged" to git, an
                    // aborted task may hold unpushed work — deleting silently is never safe.
                    case FRESH -> throw new IllegalArgumentException("Branch '" + branch
                            + "' already exists (previous run of this ticket). Decide what to do and retry with"
                            + " branchStrategy: 'recreate' (old work merged/obsolete -> delete branch, start fresh"
                            + " from " + base + ") or 'resume' (continue the existing branch and its commits).");
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
                            List.of("git", "worktree", "add", "-b", branch, worktreePath.toString(), base))
                    .expectSuccess("git worktree add " + worktreePath);
            detachUpstream(projectPath, branch);
        });
    }

    public boolean branchExists(Path projectPath, String branch) {
        return withRepoLock(projectPath, () -> processRunner.run(projectPath, GIT_TIMEOUT,
                List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/" + branch)).exitCode() == 0);
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
            reapWorktreeProcesses(worktreePath);
            var removed = processRunner.run(projectPath, GIT_TIMEOUT,
                    List.of("git", "worktree", "remove", "--force", worktreePath.toString()));
            if (removed.exitCode() != 0) {
                // git commonly UNregisters the worktree but then fails to delete the directory (a file
                // held open at that instant, IDE/build metadata). Do NOT stop here — prune the admin
                // entry and force-delete the dir below, or the worktree leaks on disk.
                log.warn("git worktree remove {} exited {}: {} — pruning and deleting the directory",
                        worktreePath, removed.exitCode(), removed.stderr());
                processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "prune"));
            }
            // Finish the job whether git deleted the tree or only unregistered it.
            forceDeleteDir(worktreePath);
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
     * Merges sourceBranch (the task branch) into targetBranch (the deploy branch) and pushes — always in a
     * dedicated deploy-side worktree cut from {@code origin/<target>}, so the merge and any conflict
     * resolution happen on the deploy side and the task branch is NEVER modified (its MR targets the base
     * branch, so touching it would balloon the diff with everything the deploy branch carries).
     *
     * <p>On conflict the deploy worktree is LEFT on disk with the conflict markers instead of aborted; the
     * human resolves it there and calls deploy again, which detects the resolved worktree and finishes the
     * push. Only this method (the backend) ever writes the shared deploy branch.
     */
    public void mergeIntoAndPush(Path projectPath, String sourceBranch, String targetBranch) {
        withRepoLock(projectPath, () -> {
            Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
            String deployBranch = "jagt-deploy-" + sourceBranch;
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            // A prior deploy left a conflicted worktree — the human has since resolved it, so finish the push.
            if (Files.isDirectory(deployWorktree)) {
                finishDeploy(projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch);
                return;
            }
            // Nothing-to-deploy guard: refuse when the source branch has no commits beyond the
            // target (empty branch, or already deployed) — deploy is decoupled from review state,
            // its ONLY precondition is that there is committed work to ship downstream.
            String ahead = processRunner.run(projectPath, GIT_TIMEOUT,
                            List.of("git", "rev-list", "--count", "origin/" + targetBranch + ".." + sourceBranch))
                    .expectSuccess("git rev-list count " + sourceBranch).stdout().trim();
            if ("0".equals(ahead)) {
                throw new IllegalStateException("Nothing to deploy: branch '" + sourceBranch
                        + "' has no commits beyond " + targetBranch + " (commit work first, or it is already deployed).");
            }
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "add",
                            "-B", deployBranch, deployWorktree.toString(), "origin/" + targetBranch))
                    .expectSuccess("git worktree add (deploy) " + targetBranch);
            // Explicit message: the merge runs on a temp branch (jagt-deploy-*), and git's
            // default "into <current branch>" would leak that name instead of the real target.
            var merge = processRunner.run(deployWorktree, GIT_TIMEOUT, List.of("git", "merge", "--no-edit",
                    "-m", "Merge branch '" + sourceBranch + "' into " + targetBranch, sourceBranch));
            if (merge.exitCode() != 0) {
                String details = merge.stderr().isBlank() ? merge.stdout() : merge.stderr();
                throw new MergeConflictException(sourceBranch, targetBranch, details, deployWorktree);
            }
            pushAndRemoveDeploy(projectPath, deployWorktree, deployBranch, targetBranch);
        });
    }

    /** Finishes a deploy whose conflicted worktree the human has resolved: commits the merge, pushes, cleans up. */
    private void finishDeploy(Path projectPath, Path deployWorktree, String deployBranch,
                              String sourceBranch, String targetBranch) {
        String unmerged = processRunner.run(deployWorktree, GIT_TIMEOUT,
                        List.of("git", "diff", "--name-only", "--diff-filter=U"))
                .expectSuccess("git unmerged paths in " + deployWorktree).stdout().trim();
        if (!unmerged.isBlank()) {
            throw new MergeConflictException(sourceBranch, targetBranch,
                    "still unresolved (git add them):\n" + unmerged, deployWorktree);
        }
        boolean mergeInProgress = processRunner.run(deployWorktree, GIT_TIMEOUT,
                List.of("git", "rev-parse", "-q", "--verify", "MERGE_HEAD")).exitCode() == 0;
        if (mergeInProgress) {
            processRunner.run(deployWorktree, GIT_TIMEOUT, List.of("git", "commit", "--no-edit"))
                    .expectSuccess("git commit (deploy resolution) " + deployWorktree);
        }
        pushAndRemoveDeploy(projectPath, deployWorktree, deployBranch, targetBranch);
    }

    /** Pushes the resolved deploy branch to the shared target, then removes the worktree — but KEEPS it on a
     *  rejected push (deploy branch moved) so the resolution isn't lost. */
    private void pushAndRemoveDeploy(Path projectPath, Path deployWorktree, String deployBranch, String targetBranch) {
        var push = processRunner.run(deployWorktree, GIT_TIMEOUT,
                List.of("git", "push", "origin", "HEAD:" + targetBranch));
        if (push.exitCode() != 0) {
            String d = push.stderr().isBlank() ? push.stdout() : push.stderr();
            throw new IllegalStateException("Deploy push to " + targetBranch + " was rejected — it moved under"
                    + " the merge. In " + deployWorktree + " run `git merge origin/" + targetBranch
                    + "`, resolve, then deploy again. Details: " + d);
        }
        processRunner.run(projectPath, GIT_TIMEOUT,
                List.of("git", "worktree", "remove", "--force", deployWorktree.toString()));
        processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "branch", "-D", deployBranch));
    }

    /** The deploy-side worktree for a task: a sibling of the repo, named after the task branch. */
    public static Path deployWorktreePath(Path projectPath, String sourceBranch) {
        return projectPath.toAbsolutePath().normalize().getParent().resolve(sourceBranch + "-deploy");
    }

    /** Removes a lingering deploy worktree and its {@code jagt-deploy-*} branch, if any (an abandoned
     *  conflict). Best-effort; no-op when absent. The caller prunes the editor's project list separately. */
    public void removeDeployWorktreeIfPresent(Path projectPath, String sourceBranch) {
        Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
        if (!Files.isDirectory(deployWorktree)) {
            return;
        }
        withRepoLock(projectPath, () -> {
            processRunner.run(projectPath, GIT_TIMEOUT,
                    List.of("git", "worktree", "remove", "--force", deployWorktree.toString()));
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "prune"));
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "branch", "-D", "jagt-deploy-" + sourceBranch));
        });
    }

    /**
     * A deploy merge hit conflicts. The conflicted checkout is LEFT on disk at {@link #deployWorktree} — a
     * dev-side worktree with the task branch merged into it — for the human to resolve there and deploy
     * again. The resolution stays on the deploy side; the task branch is never touched, so its MR (which
     * targets the base branch, not the deploy branch) keeps only the task's own change.
     * {@link #details} is git's raw conflict output (which files clashed).
     */
    public static class MergeConflictException extends IllegalStateException {
        private final transient String details;
        private final transient Path deployWorktree;

        public MergeConflictException(String sourceBranch, String targetBranch, String details, Path deployWorktree) {
            super("Merge CONFLICT merging " + sourceBranch + " into " + targetBranch + " — nothing was pushed."
                    + " Resolve it in the deploy worktree " + deployWorktree + " (this is the " + targetBranch
                    + " side; the " + sourceBranch + " branch and its MR are untouched): fix the conflicts,"
                    + " `git add` them, then deploy again to finish. Details: " + details);
            this.details = details;
            this.deployWorktree = deployWorktree;
        }

        public String details() {
            return details;
        }

        public Path deployWorktree() {
            return deployWorktree;
        }
    }

    /**
     * Left side of the `ide` diff: a throwaway detached worktree at the base branch. Reused per
     * task (previous one removed first). Pair with {@link #checkoutWorktreeCleanForDiff} for the
     * right side — the two clean checkouts are what the editor folder-diffs. Returns the path.
     */
    public Path checkoutBaseForDiff(Path projectPath, String baseBranch, String taskId) {
        return withRepoLock(projectPath, () -> {
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            Path temp = Path.of(System.getProperty("java.io.tmpdir"), "jagt-diff-" + taskId);
            clearWorktreePath(projectPath, temp);
            processRunner.run(projectPath, GIT_TIMEOUT,
                            List.of("git", "worktree", "add", "--detach", temp.toString(), baseBranch))
                    .expectSuccess("git worktree add (diff base) " + temp);
            return temp;
        });
    }

    /**
     * Right side of the `ide` diff: a clean detached worktree of the task's CURRENT tracked state
     * (committed + uncommitted), built through a throwaway index so {@code .gitignore} AND
     * {@code .git/info/exclude} are honored. This is the fix for `idea diff` on the live worktree,
     * whose raw folder compare ignores git and dumps hundreds of untracked files — the orchestrator
     * plumbing (mcp_client.js, .mcp.json, .claude/, CLAUDE.md, task_context.md, .run/) and build/IDE
     * artifacts. Snapshotting via {@code git add -A} in a temp index drops exactly those. Reused per
     * task (previous one removed first). Returns the checkout path.
     */
    public Path checkoutWorktreeCleanForDiff(Path worktreePath, Path projectPath, String baseBranch, String taskId) {
        return withRepoLock(projectPath, () -> {
            Path temp = Path.of(System.getProperty("java.io.tmpdir"), "jagt-diff-new-" + taskId);
            clearWorktreePath(projectPath, temp);
            Path index;
            try {
                index = Files.createTempFile("jagt-diff-index-" + taskId + "-", "");
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot allocate temp git index for diff of " + taskId, e);
            }
            try {
                Map<String, String> env = Map.of("GIT_INDEX_FILE", index.toString());
                processRunner.run(worktreePath, GIT_TIMEOUT, env, List.of("git", "read-tree", "HEAD"))
                        .expectSuccess("git read-tree (clean diff) " + taskId);
                processRunner.run(worktreePath, GIT_TIMEOUT, env, List.of("git", "add", "-A"))
                        .expectSuccess("git add -A (clean diff) " + taskId);
                String tree = processRunner.run(worktreePath, GIT_TIMEOUT, env, List.of("git", "write-tree"))
                        .expectSuccess("git write-tree (clean diff) " + taskId).stdout().trim();
                String commit = processRunner.run(worktreePath, GIT_TIMEOUT,
                                List.of("git", "commit-tree", tree, "-p", baseBranch, "-m", "jagt diff " + taskId))
                        .expectSuccess("git commit-tree (clean diff) " + taskId).stdout().trim();
                processRunner.run(projectPath, GIT_TIMEOUT,
                                List.of("git", "worktree", "add", "--detach", temp.toString(), commit))
                        .expectSuccess("git worktree add (clean diff) " + temp);
                return temp;
            } finally {
                try {
                    Files.deleteIfExists(index);
                } catch (IOException e) {
                    log.warn("Could not delete temp diff index {}: {}", index, e.getMessage());
                }
            }
        });
    }

    /**
     * Clears a reused diff worktree path: unregisters it (if this repo knows it), prunes stale
     * admin entries, and deletes any leftover directory on disk (a prior run — possibly of another
     * repo sharing the same taskId — can leave the path, which would fail `git worktree add`).
     */
    /**
     * Kills processes still rooted (cwd) in a worktree about to be removed — chiefly language servers
     * (jdtls, ~1-2GB): an LSP plugin typically starts its server DETACHED (own process group, to be
     * reused across editor restarts), so it orphans and survives the agent session's death rather than
     * dying with it. Best-effort, macOS {@code lsof}; failures are logged, never thrown.
     */
    private void reapWorktreeProcesses(Path worktree) {
        // Reap EVERY process whose cwd is under the worktree — NOT just java (jdtls). The agent is a Node
        // process and any of its MCP plugins may run daemons/hooks that write state into the cwd; a
        // java-only reap left those alive to repopulate the directory right after we deleted it, so the
        // worktree leaked. jagt assumes nothing about which process/plugin — cwd-under-worktree is the
        // generic, precise selector (only the task's own procs), so this handles any of them.
        var lsof = processRunner.run(null, GIT_TIMEOUT,
                List.of("lsof", "-d", "cwd", "-Fpcn"));
        // lsof reports the REAL path (symlinks resolved, e.g. macOS /var -> /private/var), so canonicalize
        // the worktree path too or the cwd comparison silently misses. Falls back to the plain absolute
        // path once the dir is already gone (a later delete pass) — nothing to reap there anyway.
        String target;
        try {
            target = worktree.toRealPath().toString();
        } catch (IOException e) {
            target = worktree.toAbsolutePath().normalize().toString();
        }
        for (Reapable r : reapable(lsof.stdout(), target)) {
            processRunner.run(null, GIT_TIMEOUT, List.of("kill", "-9", r.pid()));
            log.info("Reaped worktree-rooted process {} ({}, {})", r.pid(), r.command(), r.cwd());
        }
    }

    /** Command NEVER reaped: see {@link #reapable}. */
    private static final String VIEWER_COMMAND = "tmux";

    /** A worktree-rooted process the reap will kill — carried so the reap can log WHAT it killed. */
    record Reapable(String pid, String command, String cwd) {}

    /**
     * Picks the processes to reap from {@code lsof -d cwd -Fpcn} output: those whose cwd is at or under
     * {@code target}, EXCLUDING {@code tmux}. Field order per process set is {@code p<pid>},
     * {@code c<command>}, then {@code n<cwd>} (verified), so the command is known by the time we see
     * the cwd.
     *
     * <p>tmux is spared because every terminal driver's viewer window runs {@code tmux attach} as its
     * foreground program, so that process's cwd sits under a worktree (kitty was even launched with
     * {@code --directory <worktree>}), and the ONE shared tmux server hosts every agent. A {@code kill -9}
     * on either closes the whole viewer window / kills all agents at once — and tmux is jagt's session
     * plumbing, never a worktree-repopulating daemon (jdtls, node MCP hooks) that the reap exists to kill.
     */
    static List<Reapable> reapable(String lsofOutput, String target) {
        List<Reapable> reapable = new java.util.ArrayList<>();
        String pid = null;
        String command = null;
        for (String line : lsofOutput.lines().toList()) {
            if (line.startsWith("p")) {
                pid = line.substring(1);
                command = null;
            } else if (line.startsWith("c")) {
                command = line.substring(1);
            } else if (line.startsWith("n") && pid != null) {
                String cwd = line.substring(1);
                boolean underWorktree = cwd.equals(target) || cwd.startsWith(target + "/");
                if (underWorktree && !VIEWER_COMMAND.equals(command)) {
                    reapable.add(new Reapable(pid, command, cwd));
                    pid = null;
                }
            }
        }
        return reapable;
    }

    private void clearWorktreePath(Path projectPath, Path temp) {
        processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "remove", "--force", temp.toString()));
        processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "prune"));
        forceDeleteDir(temp);
    }

    /**
     * Delete a directory and everything under it, robustly and GENERICALLY. Some process rooted in the
     * dir may keep recreating untracked files (any agent/plugin writing state to its cwd — jagt assumes
     * nothing about which), so each pass first REAPS every process whose cwd is under the dir (killing the
     * writer, whatever it is), then re-scans and deletes. Killing-then-deleting converges: once the last
     * writer is gone a pass finds the tree static and removes it. Not tied to any specific file or plugin.
     */
    private void forceDeleteDir(Path dir) {
        for (int attempt = 0; attempt < 4 && Files.exists(dir); attempt++) {
            reapWorktreeProcesses(dir);
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (IOException e) {
                log.warn("Delete of {} failed (attempt {}): {}", dir, attempt + 1, e.getMessage());
            }
        }
        if (Files.exists(dir)) {
            log.warn("Directory {} still present after delete passes — a live process keeps repopulating it", dir);
        }
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
