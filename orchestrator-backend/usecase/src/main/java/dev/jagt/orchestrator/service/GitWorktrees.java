package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.BranchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A task's worktrees and the branches under them: cut, resumed, freed and removed. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitWorktrees {

    private final GitCommands git;

    public void createWorktree(Path projectPath, Path worktreePath, String branch, String baseBranch,
                               BranchStrategy strategy) {
        // The REMOTE-TRACKING ref, always: a fetch refreshes origin/<base> but never fast-forwards a
        // checkout-less local branch, so cutting from a local `main` inherits stale history.
        String base = "origin/" + baseBranch.replaceFirst("^origin/", "");
        git.locked(projectPath, () -> {
            git.run(projectPath, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            // An unregistered worktree whose directory is still on disk makes `git worktree add` fail.
            if (Files.exists(worktreePath)) {
                log.atWarn().setMessage("stale worktree directory cleared")
                        .addKeyValue("path", worktreePath)
                        .addKeyValue("cause", "directory present")
                        .log();
                git.clearWorktreePath(projectPath, worktreePath);
            }
            boolean branchExists = git.run(projectPath,
                    List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/" + branch)).exitCode() == 0;
            if (branchExists) {
                switch (strategy) {
                    // A reopened ticket after a squash merge looks "unmerged" to git, and an aborted task may
                    // hold unpushed work, so deleting silently is never safe.
                    case FRESH -> throw new IllegalArgumentException("Branch '" + branch
                            + "' already exists (previous run of this ticket). Decide what to do and retry with"
                            + " branchStrategy: 'recreate' (old work merged/obsolete -> delete branch, start fresh"
                            + " from " + base + ") or 'resume' (continue the existing branch and its commits).");
                    case RECREATE -> {
                        // The restore guards everything that follows: a `worktree add` failing afterwards
                        // would leave the repository detached with the branch it was on already gone.
                        Runnable restore = freeCheckout(projectPath, branch);
                        run(restore, () -> {
                            git.run(projectPath,
                                            List.of("git", "branch", "-D", branch))
                                    .expectSuccess("git branch -D " + branch);
                            cutFrom(projectPath, worktreePath, branch, base);
                        });
                        return;
                    }
                    case RESUME -> {
                        Runnable restore = freeCheckout(projectPath, branch);
                        run(restore, () -> {
                            Counts counts = countsAgainstOrigin(projectPath, branch);
                            git.run(projectPath,
                                            List.of("git", "worktree", "add", worktreePath.toString(), branch))
                                    .expectSuccess("git worktree add (resume) " + worktreePath);
                            detachUpstream(projectPath, branch);
                            fastForward(worktreePath, branch, counts.behind());
                            // A branch holding commits origin has never seen is not jagt's to publish.
                            if (counts.tracked() && counts.ahead() == 0) {
                                rebaseOntoTarget(worktreePath, branch, base);
                            }
                        });
                        return;
                    }
                }
            }
            cutFrom(projectPath, worktreePath, branch,
                    strategy == BranchStrategy.RESUME ? resumeBase(projectPath, branch, base) : base);
            if (strategy == BranchStrategy.RESUME) {
                rebaseOntoTarget(worktreePath, branch, base);
            }
        });
    }

    /**
     * Frees {@code branch} by detaching the project's OWN repository where it stands, and answers how to put it back
     * if what follows fails. Another task's worktree refuses instead, a switch carrying TRACKED changes with it.
     * Detached IN PLACE, never moved to another ref, so the files stay as the human left them.
     */
    private Runnable freeCheckout(Path projectPath, String branch) {
        Optional<Path> checkout = checkoutOf(projectPath, branch);
        // A registration whose directory was deleted by hand holds nothing. Pruning is scoped to that
        // discovery: an unconditional prune would unregister a worktree whose mount is merely away.
        if (checkout.filter(Files::isDirectory).isEmpty()) {
            if (checkout.isPresent()) {
                git.run(projectPath, List.of("git", "worktree", "prune"));
            }
            return () -> { };
        }
        Path held = checkout.get();
        if (!GitCommands.sameDirectory(held, projectPath)) {
            throw new IllegalStateException("Branch '" + branch + "' is checked out at " + held
                    + " — free it there (`git -C " + held + " switch --detach`), then run this again.");
        }
        if (!git.run(held,
                        List.of("git", "status", "--porcelain", "--untracked-files=no"))
                .expectSuccess("git status in " + held).stdout().isBlank()) {
            throw new IllegalStateException("Branch '" + branch + "' is checked out at " + held
                    + " with uncommitted changes — commit or stash them, then run this again.");
        }
        var switched = git.run(held, List.of("git", "switch", "--detach"));
        if (switched.exitCode() != 0) {
            throw new IllegalStateException("Branch '" + branch + "' is checked out at " + held
                    + " and freeing it failed: " + switched.stderr().strip());
        }
        log.atWarn().setMessage("repository detached")
                .addKeyValue("repo", held)
                .addKeyValue("branch", branch)
                .addKeyValue("effect", "branch moved to the task worktree")
                .log();
        return () -> reattach(held, branch);
    }

    /**
     * Puts a repository jagt detached back on {@code branch}, and answers what stopped it (null when it worked or
     * there was nothing to undo). Only a repository standing DETACHED AT THAT BRANCH'S TIP is touched; anything
     * else is not jagt's to move.
     */
    public String reattach(Path repository, String branch) {
        return git.locked(repository, () -> {
            if (!detachedAt(repository, branch)) {
                return null;
            }
            var switched = git.run(repository, List.of("git", "switch", branch));
            if (switched.exitCode() == 0) {
                return null;
            }
            String why = switched.stderr().isBlank() ? switched.stdout().strip() : switched.stderr().strip();
            log.atWarn().setMessage("branch restore failed")
                    .addKeyValue("repo", repository)
                    .addKeyValue("branch", branch)
                    .addKeyValue("cause", why)
                    .log();
            return why;
        });
    }

    private boolean detachedAt(Path repository, String branch) {
        if (git.run(repository, List.of("git", "symbolic-ref", "-q", "HEAD"))
                .exitCode() == 0) {
            return false;                                        // on a branch: nothing jagt detached
        }
        String head = git.run(repository, List.of("git", "rev-parse", "HEAD"))
                .stdout().strip();
        String tip = git.run(repository, List.of("git", "rev-parse", branch))
                .stdout().strip();
        return !head.isBlank() && head.equals(tip);
    }

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

    /** What separates a branch from origin's copy of it: {@code tracked} is false where origin has none. */
    private record Counts(boolean tracked, long behind, long ahead) {
    }

    /**
     * Refuses outright where both sides carry commits: a branch rewritten on the host is the human's to reconcile,
     * and moving over what only this machine has would lose it.
     */
    private Counts countsAgainstOrigin(Path projectPath, String branch) {
        if (!remoteRefExists(projectPath, branch)) {
            return new Counts(false, 0, 0);
        }
        String[] counted = git.run(projectPath, List.of("git", "rev-list", "--left-right",
                        "--count", "origin/" + branch + "..." + branch))
                .expectSuccess("git rev-list --count " + branch).stdout().strip().split("\\s+");
        long behind = Long.parseLong(counted[0]);
        long ahead = Long.parseLong(counted[1]);
        if (behind > 0 && ahead > 0) {
            throw new IllegalStateException("Branch '" + branch + "' and origin/" + branch + " have diverged ("
                    + ahead + " commit(s) only here, " + behind + " only on origin) — it was rewritten on the"
                    + " host. Reconcile it yourself, then resume.");
        }
        return new Counts(true, behind, ahead);
    }

    /**
     * A fetch moves no local ref, so a branch pushed to elsewhere would resume stale and its first merge of the
     * target restage every commit the request already carries. Run inside the worktree, the branch being checked
     * out there: moving the ref from the repository is refused by git.
     */
    private void fastForward(Path worktreePath, String branch, long behind) {
        if (behind == 0) {
            return;
        }
        git.run(worktreePath, List.of("git", "merge", "--ff-only", "origin/" + branch))
                .expectSuccess("git merge --ff-only origin/" + branch);
        log.atInfo().setMessage("resumed branch fast-forwarded")
                .addKeyValue("branch", branch)
                .addKeyValue("commits", behind)
                .log();
    }

    /**
     * The target moves on while a request waits, so a resumed branch is replayed on top of it and pushed back under
     * a lease — the only rewrite of a pushed branch jagt makes, and only of the task's own. A CONFLICTING rebase is
     * left standing in the worktree: resolving it is the session's first job. A push the lease refuses undoes the
     * rebase, so the branch never sits rewritten here and whole on origin.
     */
    private void rebaseOntoTarget(Path worktreePath, String branch, String target) {
        // The request outliving its target is an ordinary case: there is then nothing to replay onto.
        if (git.run(worktreePath,
                List.of("git", "rev-parse", "--verify", "--quiet", target)).exitCode() != 0) {
            return;
        }
        String before = headOf(worktreePath);
        var rebased = git.run(worktreePath, List.of("git", "rebase", target));
        if (rebased.exitCode() != 0) {
            // A rebase that never started is not a conflict: one left standing is what the session resolves.
            if (!rebaseStanding(worktreePath)) {
                throw new IllegalStateException("Rebasing '" + branch + "' onto " + target + " failed: "
                        + rebased.stderr().strip());
            }
            log.atWarn().setMessage("resume rebase conflicted")
                    .addKeyValue("branch", branch)
                    .addKeyValue("target", target)
                    .addKeyValue("cause", rebased.stderr().strip())
                    .addKeyValue("effect", "left in the worktree for the session to resolve")
                    .log();
            return;
        }
        if (before.equals(headOf(worktreePath))) {
            return;
        }
        var pushed = git.run(worktreePath, List.of("git", "push", "--force-with-lease",
                "origin", "refs/heads/" + branch + ":refs/heads/" + branch));
        if (pushed.exitCode() != 0) {
            // The push's own words are the diagnosis, so a failed undo rides along rather than replacing them.
            var undone = git.run(worktreePath, List.of("git", "reset", "--hard", before));
            throw new IllegalStateException("Branch '" + branch + "' was rebased onto " + target + " and the"
                    + " push was refused: " + pushed.stderr().strip()
                    + (undone.exitCode() == 0 ? " — the rebase was undone."
                            : " — undoing the rebase ALSO failed: " + undone.stderr().strip()));
        }
        log.atInfo().setMessage("resumed branch rebased")
                .addKeyValue("branch", branch)
                .addKeyValue("target", target)
                .log();
    }

    /** Whether a rebase is standing in the worktree: git keeps one under a directory of its own. */
    private boolean rebaseStanding(Path worktreePath) {
        return List.of("rebase-merge", "rebase-apply").stream()
                .map(kept -> git.run(worktreePath,
                        List.of("git", "rev-parse", "--git-path", kept)).stdout().strip())
                .anyMatch(path -> !path.isBlank() && Files.exists(worktreePath.resolve(path)));
    }

    private String headOf(Path worktreePath) {
        return git.run(worktreePath, List.of("git", "rev-parse", "HEAD"))
                .expectSuccess("git rev-parse HEAD in " + worktreePath).stdout().strip();
    }

    /** A resumed branch this machine never had comes from the REQUEST's branch; its target holds none of the work. */
    private String resumeBase(Path projectPath, String branch, String base) {
        return remoteRefExists(projectPath, branch) ? "origin/" + branch : base;
    }

    /** The just-fetched remote-tracking ref, asked of refs rather than over the network. */
    private boolean remoteRefExists(Path projectPath, String branch) {
        return git.run(projectPath,
                List.of("git", "rev-parse", "--verify", "--quiet", "refs/remotes/origin/" + branch))
                .exitCode() == 0;
    }

    private void cutFrom(Path projectPath, Path worktreePath, String branch, String base) {
        git.run(projectPath,
                        List.of("git", "worktree", "add", "-b", branch, worktreePath.toString(), base))
                .expectSuccess("git worktree add " + worktreePath);
        detachUpstream(projectPath, branch);
    }

    /** Which worktree (the base repo included) has {@code branch} checked out, if any. */
    private Optional<Path> checkoutOf(Path projectPath, String branch) {
        var listed = git.run(projectPath,
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

    public boolean branchExists(Path projectPath, String branch) {
        return git.locked(projectPath, () -> git.run(projectPath,
                List.of("git", "rev-parse", "--verify", "--quiet", "refs/heads/" + branch)).exitCode() == 0);
    }

    /** Commits on the branch, or on its copy at origin, that the base does not hold: work a recreate would lose. */
    public boolean holdsOwnCommits(Path projectPath, String branch, String baseBranch) {
        String base = "origin/" + baseBranch.replaceFirst("^origin/", "");
        return git.locked(projectPath, () -> {
            List<String> command = new ArrayList<>(List.of("git", "rev-list", "--count", "^" + base,
                    "refs/heads/" + branch));
            if (remoteRefExists(projectPath, branch)) {
                command.add("refs/remotes/origin/" + branch);
            }
            return !git.run(projectPath, command)
                    .expectSuccess("git rev-list --count " + branch).stdout().strip().equals("0");
        });
    }

    /** Asked over the network, not of {@code refs/remotes}: a just-pushed branch is absent until the next fetch. */
    public boolean remoteBranchExists(Path projectPath, String branch) {
        return git.locked(projectPath, () -> git.run(projectPath,
                List.of("git", "ls-remote", "--exit-code", "--heads", "origin", branch)).exitCode() == 0);
    }

    /**
     * A new branch inherits the base as upstream, so a bare {@code git push} from an agent would target the release
     * branch. Without an upstream it errors instead.
     */
    private void detachUpstream(Path projectPath, String branch) {
        git.run(projectPath, List.of("git", "branch", "--unset-upstream", branch));
    }

    /** Deletes {@code branchToDelete} too when non-null. Best-effort: failures are logged, not thrown. */
    public void removeWorktree(Path projectPath, Path worktreePath, String branchToDelete) {
        git.locked(projectPath, () -> {
            git.reap(worktreePath);
            var removed = git.run(projectPath,
                    List.of("git", "worktree", "remove", "--force", worktreePath.toString()));
            if (removed.exitCode() != 0) {
                // git often unregisters the worktree and still fails to delete the directory, leaking it.
                log.atWarn().setMessage("git worktree remove failed")
                        .addKeyValue("path", worktreePath)
                        .addKeyValue("exit", removed.exitCode())
                        .addKeyValue("cause", removed.stderr())
                        .addKeyValue("effect", "pruned and deleted")
                        .log();
                git.run(projectPath, List.of("git", "worktree", "prune"));
            }
            git.forceDeleteDir(worktreePath);
            if (branchToDelete != null) {
                var branch = git.run(projectPath,
                        List.of("git", "branch", "-D", branchToDelete));
                if (branch.exitCode() != 0) {
                    log.atWarn().setMessage("git branch delete failed")
                            .addKeyValue("branch", branchToDelete)
                            .addKeyValue("cause", branch.stderr())
                            .log();
                }
            }
        });
    }

    /** Best-effort: nothing is thrown when the removal fails. */
    public void removeDeployWorktreeIfPresent(Path projectPath, String sourceBranch) {
        Path deployWorktree = GitDeploy.deployWorktreePath(projectPath, sourceBranch);
        if (!Files.isDirectory(deployWorktree)) {
            return;
        }
        git.locked(projectPath, () -> {
            git.run(projectPath,
                    List.of("git", "worktree", "remove", "--force", deployWorktree.toString()));
            git.run(projectPath, List.of("git", "worktree", "prune"));
            git.run(projectPath, List.of("git", "branch", "-D", "jagt-deploy-" + sourceBranch));
        });
    }

    /** A diff checkout outlives the viewer that opened it, and only the task's own retirement knows it is over. */
    public void removeDiffWorktrees(Path projectPath, String taskId, String project) {
        git.locked(projectPath,
                () -> DiffCheckouts.diffWorktreePaths(taskId, project).forEach(temp -> git.clearWorktreePath(projectPath, temp)));
    }

    public String remoteUrl(Path projectPath) {
        return git.locked(projectPath, () ->
                git.run(projectPath, List.of("git", "remote", "get-url", "origin"))
                        .expectSuccess("git remote get-url origin in " + projectPath)
                        .stdout()
                        .trim());
    }

    public Path gitCommonDir(Path projectPath) {
        return git.locked(projectPath, () -> {
            String dir = git.run(projectPath,
                            List.of("git", "rev-parse", "--path-format=absolute", "--git-common-dir"))
                    .expectSuccess("git rev-parse --git-common-dir")
                    .stdout();
            return Path.of(dir.trim());
        });
    }
}
