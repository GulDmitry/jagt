package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * All Git operations against a base repository are serialized with a
 * per-repository ReentrantLock to avoid .git/index.lock races between
 * concurrent agents. Different repositories don't contend: a slow
 * `git fetch` in one project must not block initializing tasks in another.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitService {

    private static final Duration GIT_TIMEOUT = Duration.ofMinutes(3);

    private final ConcurrentHashMap<String, ReentrantLock> repoLocks = new ConcurrentHashMap<>();
    private final ProcessRunner processRunner;

    public enum BranchStrategy { FRESH, RECREATE, RESUME }

    public void createWorktree(Path projectPath, Path worktreePath, String branch, String baseBranch,
                               BranchStrategy strategy) {
        // The REMOTE-TRACKING ref, always: a fetch refreshes origin/<base> but never fast-forwards a
        // checkout-less local branch, so cutting from a local `main` inherits stale history.
        String base = "origin/" + baseBranch.replaceFirst("^origin/", "");
        withRepoLock(projectPath, () -> {
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            // A previous `done` can unregister a worktree and still fail to delete the directory, which
            // makes `git worktree add` fail "already exists".
            if (Files.exists(worktreePath)) {
                log.warn("Clearing a stale leftover worktree directory before creating {}", worktreePath);
                clearWorktreePath(projectPath, worktreePath);
            }
            boolean branchExists = processRunner.run(projectPath, GIT_TIMEOUT,
                    List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/" + branch)).exitCode() == 0;
            if (branchExists) {
                switch (strategy) {
                    // A reopened ticket after a squash merge looks "unmerged" to git, an
                    // aborted task may hold unpushed work — deleting silently is never safe. Nothing is freed
                    // on this path: a refusal must leave the human's repository where it was.
                    case FRESH -> throw new IllegalArgumentException("Branch '" + branch
                            + "' already exists (previous run of this ticket). Decide what to do and retry with"
                            + " branchStrategy: 'recreate' (old work merged/obsolete -> delete branch, start fresh"
                            + " from " + base + ") or 'resume' (continue the existing branch and its commits).");
                    case RECREATE -> {
                        // The restore guards everything that follows, not just the delete: a `worktree add`
                        // that fails afterwards would otherwise leave the repository detached with the branch
                        // it was on already gone.
                        Runnable restore = freeCheckout(projectPath, branch);
                        run(restore, () -> {
                            processRunner.run(projectPath, GIT_TIMEOUT,
                                            List.of("git", "branch", "-D", branch))
                                    .expectSuccess("git branch -D " + branch);
                            cutFrom(projectPath, worktreePath, branch, base);
                        });
                        return;
                    }
                    case RESUME -> {
                        Runnable restore = freeCheckout(projectPath, branch);
                        run(restore, () -> {
                            processRunner.run(projectPath, GIT_TIMEOUT,
                                            List.of("git", "worktree", "add", worktreePath.toString(), branch))
                                    .expectSuccess("git worktree add (resume) " + worktreePath);
                            detachUpstream(projectPath, branch);
                        });
                        return;
                    }
                }
            }
            cutFrom(projectPath, worktreePath, branch, base);
        });
    }

    /**
     * Frees {@code branch} for a worktree by detaching the project's OWN repository where it stands, and answers
     * how to put that repository back if what follows fails. Nobody works in the base repository, so a task
     * blocked on a checkout nobody remembers making is worse than a warning — but another worktree belongs to
     * another task, and a switch would carry TRACKED changes with it, so both of those refuse instead.
     *
     * <p>Detached IN PLACE, never moved to another ref: the files stay exactly as the human left them, so an
     * editor open on that directory sees no change, and a per-task base with no local branch is no obstacle.
     */
    private Runnable freeCheckout(Path projectPath, String branch) {
        Optional<Path> checkout = checkoutOf(projectPath, branch);
        // A registration whose directory somebody deleted by hand holds nothing. Pruning is scoped to that
        // discovery: an unconditional prune would also unregister a worktree whose mount happens to be away.
        if (checkout.filter(Files::isDirectory).isEmpty()) {
            if (checkout.isPresent()) {
                processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "prune"));
            }
            return () -> { };
        }
        Path held = checkout.get();
        if (!sameDirectory(held, projectPath)) {
            throw new IllegalStateException("Branch '" + branch + "' is checked out at " + held
                    + " — free it there (`git -C " + held + " switch --detach`), then run this again.");
        }
        if (!processRunner.run(held, GIT_TIMEOUT,
                        List.of("git", "status", "--porcelain", "--untracked-files=no"))
                .expectSuccess("git status in " + held).stdout().isBlank()) {
            throw new IllegalStateException("Branch '" + branch + "' is checked out at " + held
                    + " with uncommitted changes — commit or stash them, then run this again.");
        }
        var switched = processRunner.run(held, GIT_TIMEOUT, List.of("git", "switch", "--detach"));
        if (switched.exitCode() != 0) {
            throw new IllegalStateException("Branch '" + branch + "' is checked out at " + held
                    + " and freeing it failed: " + switched.stderr().strip());
        }
        log.atWarn().addKeyValue("task", branch)
                .log("{} was on '{}', which this task needs — detached it there, files untouched. The branch"
                        + " itself moves to the task's worktree.", held, branch);
        return () -> reattach(held, branch);
    }

    /**
     * Puts a repository jagt detached back on {@code branch}, and answers what stopped it (null when it worked or
     * when there was nothing to undo). Only a repository standing DETACHED AT THAT BRANCH'S TIP is touched: that
     * is the state {@code freeCheckout} leaves, and anything else — a branch of its own, a bisect, a checkout the
     * human moved since — is not jagt's to move.
     */
    public String reattach(Path repository, String branch) {
        return withRepoLock(repository, () -> {
            if (!detachedAt(repository, branch)) {
                return null;
            }
            var switched = processRunner.run(repository, GIT_TIMEOUT, List.of("git", "switch", branch));
            if (switched.exitCode() == 0) {
                return null;
            }
            String why = switched.stderr().isBlank() ? switched.stdout().strip() : switched.stderr().strip();
            log.warn("Could not put {} back on '{}': {}", repository, branch, why);
            return why;
        });
    }

    private boolean detachedAt(Path repository, String branch) {
        if (processRunner.run(repository, GIT_TIMEOUT, List.of("git", "symbolic-ref", "-q", "HEAD"))
                .exitCode() == 0) {
            return false;                                        // on a branch: nothing jagt detached
        }
        String head = processRunner.run(repository, GIT_TIMEOUT, List.of("git", "rev-parse", "HEAD"))
                .stdout().strip();
        String tip = processRunner.run(repository, GIT_TIMEOUT, List.of("git", "rev-parse", branch))
                .stdout().strip();
        return !head.isBlank() && head.equals(tip);
    }

    /** Undoes the checkout jagt moved when the step it moved it FOR does not land. */
    private static void run(Runnable restoreOnFailure, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            try {
                restoreOnFailure.run();
            } catch (RuntimeException restoreFailed) {
                // The step's failure is the answer the human needs; a restore that also failed rides along.
                e.addSuppressed(restoreFailed);
            }
            throw e;
        }
    }

    private void cutFrom(Path projectPath, Path worktreePath, String branch, String base) {
        processRunner.run(projectPath, GIT_TIMEOUT,
                        List.of("git", "worktree", "add", "-b", branch, worktreePath.toString(), base))
                .expectSuccess("git worktree add " + worktreePath);
        detachUpstream(projectPath, branch);
    }

    /** Which worktree (the base repo included) has {@code branch} checked out, if any. */
    private Optional<Path> checkoutOf(Path projectPath, String branch) {
        var listed = processRunner.run(projectPath, GIT_TIMEOUT,
                List.of("git", "worktree", "list", "--porcelain"));
        Path worktree = null;
        for (String line : listed.stdout().lines().map(String::strip).toList()) {
            if (line.startsWith("worktree ")) {
                worktree = Path.of(line.substring("worktree ".length()));
            } else if (line.equals("branch refs/heads/" + branch)) {
                return Optional.ofNullable(worktree);
            }
        }
        return Optional.empty();
    }

    /** One branch name per line, blanks dropped — the shape of {@code --format=%(refname:short)} output. */
    static List<String> branchNames(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return List.of();
        }
        return stdout.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    /** What a ship committed, so the caller can report the truth instead of assuming a commit happened. */
    public record Commit(boolean created, int changedFiles) {
    }

    /** Stages everything in the worktree and commits it. Nothing staged = no commit, and not an error. */
    public Commit commitAll(Path projectPath, Path worktree, String message) {
        return withRepoLock(projectPath, () -> {
            processRunner.run(worktree, GIT_TIMEOUT, List.of("git", "add", "-A"))
                    .expectSuccess("git add -A in " + worktree);
            List<String> staged = branchNames(processRunner.run(worktree, GIT_TIMEOUT,
                            List.of("git", "diff", "--cached", "--name-only"))
                    .expectSuccess("git diff --cached in " + worktree).stdout());
            if (staged.isEmpty()) {
                return new Commit(false, 0);
            }
            processRunner.run(worktree, GIT_TIMEOUT, List.of("git", "commit", "-m", message))
                    .expectSuccess("git commit in " + worktree);
            return new Commit(true, staged.size());
        });
    }

    /**
     * Pushes ONE branch, with a refspec explicit on both sides so it cannot follow HEAD or an upstream.
     * Never {@code --force} (a diverged branch is a human's call) and never {@code -u} (see
     * {@link #detachUpstream}).
     */
    public void pushBranch(Path projectPath, Path worktree, String branch) {
        withRepoLock(projectPath, () -> {
            var push = processRunner.run(worktree, GIT_TIMEOUT, List.of("git", "push", "origin",
                    "refs/heads/" + branch + ":refs/heads/" + branch));
            if (push.exitCode() != 0) {
                String details = push.stderr().isBlank() ? push.stdout() : push.stderr();
                throw new IllegalStateException("Could not push branch '" + branch + "': " + details.strip());
            }
        });
    }

    public boolean branchExists(Path projectPath, String branch) {
        return withRepoLock(projectPath, () -> processRunner.run(projectPath, GIT_TIMEOUT,
                List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/" + branch)).exitCode() == 0);
    }

    /**
     * Does {@code origin} carry this branch? Asked over the network, not of {@code refs/remotes}: a branch
     * pushed a minute ago is absent locally until the next fetch.
     */
    public boolean remoteBranchExists(Path projectPath, String branch) {
        return withRepoLock(projectPath, () -> processRunner.run(projectPath, GIT_TIMEOUT,
                List.of("git", "ls-remote", "--exit-code", "--heads", "origin", branch)).exitCode() == 0);
    }

    /**
     * CRITICAL SAFETY: a new branch inherits the base as upstream, so a bare {@code git push} from an agent
     * would target the release branch. Without an upstream it errors instead.
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
                // git often unregisters the worktree and still fails to delete the directory (a file held
                // open), so stopping here leaks it on disk.
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
     * Merges the task branch into the deploy branch and pushes, in a dedicated worktree cut from
     * {@code origin/<target>} — so the task branch is NEVER modified by a deploy.
     *
     * <p>A conflict LEAVES that worktree on disk with its markers instead of aborting: the next call detects
     * the resolved worktree and finishes the push.
     */
    public String mergeIntoAndPush(Path projectPath, String sourceBranch, String targetBranch) {
        return withRepoLock(projectPath, () -> {
            Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
            String deployBranch = "jagt-deploy-" + sourceBranch;
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            // A prior deploy left a conflicted worktree — the human has since resolved it, so finish the push.
            if (Files.isDirectory(deployWorktree)) {
                if (!hasDeployWorktree(projectPath, sourceBranch)) {
                    throw new ForeignDeployWorktreeException(deployWorktree, projectPath);
                }
                return finishDeploy(projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch);
            }
            // Deploy is decoupled from review state: its ONLY precondition is committed work to ship.
            String ahead = processRunner.run(projectPath, GIT_TIMEOUT,
                            List.of("git", "rev-list", "--count", "origin/" + targetBranch + ".." + sourceBranch))
                    .expectSuccess("git rev-list count " + sourceBranch).stdout().trim();
            if ("0".equals(ahead)) {
                throw new NothingToDeployException(sourceBranch, targetBranch);
            }
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "add",
                            "-B", deployBranch, deployWorktree.toString(), "origin/" + targetBranch))
                    .expectSuccess("git worktree add (deploy) " + targetBranch);
            // The message is explicit because git's default would name the throwaway branch, not the target.
            // --no-ff ALWAYS: that one merge commit is what `revert` undoes — a fast-forward would leave the
            // commits loose, and "the deploy" would become a range.
            var merge = processRunner.run(deployWorktree, GIT_TIMEOUT, List.of("git", "merge", "--no-ff",
                    "--no-edit", "-m", "Merge branch '" + sourceBranch + "' into " + targetBranch,
                    sourceBranch));
            if (merge.exitCode() != 0) {
                String details = merge.stderr().isBlank() ? merge.stdout() : merge.stderr();
                // Only UNMERGED PATHS mean a conflict. git also exits non-zero for a missing committer
                // identity or a refusing hook, and calling those a conflict leaves a worktree behind that the
                // next deploy treats as "resolved" and pushes.
                if (unmergedPaths(deployWorktree).isBlank()) {
                    removeDeployWorktree(projectPath, deployWorktree, deployBranch);
                    throw new IllegalStateException("Could not merge " + sourceBranch + " into " + targetBranch
                            + " — nothing was pushed and no conflict is waiting for you; git said: " + details);
                }
                throw new MergeConflictException(sourceBranch, targetBranch, details, deployWorktree);
            }
            return pushAndRemoveDeploy(projectPath, deployWorktree, deployBranch, targetBranch);
        });
    }

    /** Finishes a deploy whose conflicted worktree the human has resolved: commits the merge, pushes, cleans up. */
    private String finishDeploy(Path projectPath, Path deployWorktree, String deployBranch,
                                String sourceBranch, String targetBranch) {
        String unmerged = unmergedPaths(deployWorktree);
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
        return pushAndRemoveDeploy(projectPath, deployWorktree, deployBranch, targetBranch);
    }

    /** Pushes the resolved deploy branch to the shared target, then removes the worktree — but KEEPS it on a
     *  rejected push (deploy branch moved) so the resolution isn't lost. Returns the commit that was pushed:
     *  the merge `revert` will have to undo, which is knowable only here, before the worktree is gone. */
    private String pushAndRemoveDeploy(Path projectPath, Path deployWorktree, String deployBranch, String targetBranch) {
        var push = processRunner.run(deployWorktree, GIT_TIMEOUT,
                List.of("git", "push", "origin", "HEAD:" + targetBranch));
        if (push.exitCode() != 0) {
            String d = push.stderr().isBlank() ? push.stdout() : push.stderr();
            throw new IllegalStateException("Deploy push to " + targetBranch + " was rejected — it moved under"
                    + " the merge. In " + deployWorktree + " run `git merge origin/" + targetBranch
                    + "`, resolve, then deploy again. Details: " + d);
        }
        String merged = processRunner.run(deployWorktree, GIT_TIMEOUT, List.of("git", "rev-parse", "HEAD"))
                .expectSuccess("git rev-parse HEAD in " + deployWorktree).stdout().trim();
        removeDeployWorktree(projectPath, deployWorktree, deployBranch);
        return merged;
    }

    /** Paths git left unmerged — the only thing that distinguishes a conflict from a failed merge. */
    private String unmergedPaths(Path worktree) {
        return processRunner.run(worktree, GIT_TIMEOUT,
                        List.of("git", "diff", "--name-only", "--diff-filter=U"))
                .expectSuccess("git unmerged paths in " + worktree).stdout().trim();
    }

    /** Drops the throwaway deploy checkout and its temp branch. Best-effort: it is scaffolding, not state. */
    private void removeDeployWorktree(Path projectPath, Path deployWorktree, String deployBranch) {
        processRunner.run(projectPath, GIT_TIMEOUT,
                List.of("git", "worktree", "remove", "--force", deployWorktree.toString()));
        processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "branch", "-D", deployBranch));
    }

    /**
     * Undoes ONE deploy: reverts {@code mergeCommit} on {@code targetBranch} and pushes. Only ever ADDS a
     * commit — no rewrite, no force-push — and leaves the task branch's commits alone, which is what makes
     * "fix and ship again" possible.
     *
     * <p>Refuses rather than guess when the commit is not on the branch, is not a merge, was already reverted,
     * or the revert conflicts with later work. That last one is aborted and cleaned up: unlike a deploy
     * conflict, there is nothing useful for a human to finish there.
     *
     * @return the revert commit pushed to {@code targetBranch}
     */
    public String revertMergeAndPush(Path projectPath, String sourceBranch, String targetBranch,
                                     String mergeCommit) {
        return withRepoLock(projectPath, () -> {
            Path revertWorktree = revertWorktreePath(projectPath, sourceBranch);
            String revertBranch = "jagt-revert-" + sourceBranch;
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            if (Files.exists(revertWorktree)) {
                clearWorktreePath(projectPath, revertWorktree);
            }
            processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "worktree", "add",
                            "-B", revertBranch, revertWorktree.toString(), "origin/" + targetBranch))
                    .expectSuccess("git worktree add (revert) " + targetBranch);
            try {
                requireRevertable(revertWorktree, targetBranch, mergeCommit);
                requireMergeCommit(revertWorktree, mergeCommit, targetBranch);
                // -m 1: a merge has two parents, and reverting it means "undo what the SECOND parent brought
                // in", i.e. keep the target branch's own line of history. Without it git refuses outright.
                var revert = processRunner.run(revertWorktree, GIT_TIMEOUT, List.of("git", "revert",
                        "-m", "1", "--no-edit", mergeCommit));
                if (revert.exitCode() != 0) {
                    processRunner.run(revertWorktree, GIT_TIMEOUT, List.of("git", "revert", "--abort"));
                    String details = revert.stderr().isBlank() ? revert.stdout() : revert.stderr();
                    throw new IllegalStateException("Cannot revert " + shortSha(mergeCommit) + " on "
                            + targetBranch + ": the revert conflicts with work done there since the deploy."
                            + " Decide what should survive and revert by hand: `git revert -m 1 "
                            + shortSha(mergeCommit) + "`. Details: " + details);
                }
                var push = processRunner.run(revertWorktree, GIT_TIMEOUT,
                        List.of("git", "push", "origin", "HEAD:" + targetBranch));
                if (push.exitCode() != 0) {
                    String details = push.stderr().isBlank() ? push.stdout() : push.stderr();
                    throw new IllegalStateException("Revert push to " + targetBranch + " was rejected — it"
                            + " moved while the revert was being made. Try revert again. Details: " + details);
                }
                return processRunner.run(revertWorktree, GIT_TIMEOUT, List.of("git", "rev-parse", "HEAD"))
                        .expectSuccess("git rev-parse HEAD in " + revertWorktree).stdout().trim();
            } finally {
                // A revert worktree holds no human decision worth keeping, unlike a conflicted deploy.
                processRunner.run(projectPath, GIT_TIMEOUT,
                        List.of("git", "worktree", "remove", "--force", revertWorktree.toString()));
                processRunner.run(projectPath, GIT_TIMEOUT, List.of("git", "branch", "-D", revertBranch));
            }
        });
    }

    /**
     * Only a merge can be reverted as ONE unit, and every deploy makes one ({@code --no-ff}). A non-merge here
     * means reverting would undo one commit of a task instead of the deploy.
     */
    private void requireMergeCommit(Path revertWorktree, String mergeCommit, String targetBranch) {
        String parents = processRunner.run(revertWorktree, GIT_TIMEOUT,
                        List.of("git", "rev-list", "--parents", "-n", "1", mergeCommit))
                .expectSuccess("git rev-list --parents " + mergeCommit).stdout().trim();
        if (parents.split("\\s+").length < 3) {
            throw new IllegalStateException("Commit " + shortSha(mergeCommit) + " on " + targetBranch
                    + " is not a merge, so reverting it would undo only part of what was deployed. Revert by"
                    + " hand after deciding what should come out. Nothing was reverted.");
        }
    }

    /** The two "this is not the situation you think it is" checks, refused before anything is written. */
    private void requireRevertable(Path revertWorktree, String targetBranch, String mergeCommit) {
        boolean onBranch = processRunner.run(revertWorktree, GIT_TIMEOUT,
                List.of("git", "merge-base", "--is-ancestor", mergeCommit, "HEAD")).exitCode() == 0;
        if (!onBranch) {
            throw new IllegalStateException("Commit " + shortSha(mergeCommit) + " is not on " + targetBranch
                    + " — it was never deployed there, or that history was rewritten. Nothing was reverted.");
        }
        // git's own revert message is the marker, so a revert made by hand counts too.
        String existing = processRunner.run(revertWorktree, GIT_TIMEOUT, List.of("git", "log",
                        "--fixed-strings", "--grep=This reverts commit " + mergeCommit, "--format=%H", "-1"))
                .expectSuccess("git log (revert search) in " + revertWorktree).stdout().trim();
        if (!existing.isBlank()) {
            throw new IllegalStateException("Commit " + shortSha(mergeCommit) + " was already reverted on "
                    + targetBranch + " by " + shortSha(existing) + ". Nothing to do.");
        }
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
    }

    /** The deploy-side worktree for a task: a sibling of the repo, named after the task branch. */
    public static Path deployWorktreePath(Path projectPath, String sourceBranch) {
        return projectPath.toAbsolutePath().normalize().getParent().resolve(sourceBranch + "-deploy");
    }

    /**
     * Whether a deploy worktree for this task is waiting in THIS repository. The path is derived from the
     * repository's PARENT directory, so the sibling repositories of one task all derive the SAME one — the
     * directory alone cannot say whose conflict is sitting in it, and finishing one repository's merge from
     * another's checkout would push its content to the wrong remote.
     */
    public boolean hasDeployWorktree(Path projectPath, String sourceBranch) {
        return worktreeOwner(deployWorktreePath(projectPath, sourceBranch))
                .filter(owner -> sameDirectory(owner, projectPath))
                .isPresent();
    }

    /** The repository a checkout belongs to, empty when it is not a checkout at all. */
    private Optional<Path> worktreeOwner(Path worktree) {
        if (!Files.isDirectory(worktree)) {
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
    private static boolean sameDirectory(Path one, Path other) {
        try {
            return Files.isSameFile(one, other);
        } catch (IOException e) {
            return one.toAbsolutePath().normalize().equals(other.toAbsolutePath().normalize());
        }
    }

    /** Where a revert is staged — separate from the deploy worktree, which may be sitting in a conflict. */
    public static Path revertWorktreePath(Path projectPath, String sourceBranch) {
        return projectPath.toAbsolutePath().normalize().getParent().resolve(sourceBranch + "-revert");
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
     * The deploy worktree path this repository derives is occupied by a checkout it did not cut — a sibling's
     * stalled merge, or a directory whose git metadata is gone. Finishing it here would push that work to this
     * repository's remote, so nothing is attempted; typed so a caller landing SEVERAL repositories can come back
     * to this one once the path is free.
     */
    public static class ForeignDeployWorktreeException extends IllegalStateException {

        public ForeignDeployWorktreeException(Path deployWorktree, Path projectPath) {
            super("The deploy worktree path " + deployWorktree + " holds a checkout "
                    + projectPath.getFileName() + " did not cut, so its merge cannot be finished here. Deal with"
                    + " that checkout first — finish its deploy, or `git worktree remove --force` it if it is"
                    + " stale.");
        }
    }

    /**
     * The branch holds nothing the target does not already have. Typed so a caller landing SEVERAL repositories
     * can tell "there was nothing to do here" apart from a failure.
     */
    public static class NothingToDeployException extends IllegalStateException {

        public NothingToDeployException(String sourceBranch, String targetBranch) {
            super("Nothing to deploy: branch '" + sourceBranch + "' has no commits beyond " + targetBranch
                    + " (commit work first, or it is already deployed).");
        }
    }

    /**
     * A deploy merge hit conflicts. The checkout is LEFT at {@link #deployWorktree} for the human to resolve
     * and deploy again; the task branch is never touched, so its request keeps only the task's own change.
     * {@link #details} is git's raw conflict output.
     */
    public static class MergeConflictException extends IllegalStateException {
        private final transient String details;
        private final transient Path deployWorktree;

        public MergeConflictException(String sourceBranch, String targetBranch, String details, Path deployWorktree) {
            super("Merge CONFLICT merging " + sourceBranch + " into " + targetBranch + " — nothing was pushed."
                    + " Resolve it in the deploy worktree " + deployWorktree + " (this is the " + targetBranch
                    + " side; the " + sourceBranch + " branch and its review request are untouched): fix"
                    + " the conflicts, `git add` them, then deploy again to finish. Details: " + details);
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
     * Left side of the `ide` diff: a throwaway detached worktree at the base branch, reused per task. Pair it
     * with {@link #checkoutWorktreeCleanForDiff} — the editor folder-diffs the two clean checkouts.
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
     * Right side of the `ide` diff: the task's CURRENT tracked state, committed or not, snapshotted through a
     * throwaway index so {@code .gitignore} and {@code .git/info/exclude} are honored — a raw folder compare
     * of the live worktree dumps hundreds of untracked files instead. Reused per task.
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
        try {
            reapWorktreeProcessesOrThrow(worktree);
        } catch (RuntimeException e) {
            // Hygiene, not state: a machine without `lsof` (most minimal Linux images) or a kill that is
            // refused must never stop a worktree from being removed — that would turn `done` into a no-op.
            log.warn("Could not reap processes rooted in {} ({}) — removing it anyway; a language server may"
                    + " survive and hold memory", worktree, e.getMessage());
        }
    }

    private void reapWorktreeProcessesOrThrow(Path worktree) {
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
     * {@code c<command>}, then {@code n<cwd>}, so the command is known by the time we see
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
