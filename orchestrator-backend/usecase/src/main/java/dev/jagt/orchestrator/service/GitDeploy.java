package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The only writes to a shared branch: a deploy's merge, and the revert that undoes it. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitDeploy {

    private final GitCommands git;

    /**
     * Merges the task branch into the deploy branch and pushes, in a worktree cut from {@code origin/<target>} so
     * the task branch is NEVER modified. A conflict LEAVES that worktree with its markers instead of aborting; the
     * next call finishes a resolution waiting there, and otherwise merges again against the target as it is then.
     */
    public String mergeIntoAndPush(Path projectPath, String sourceBranch, String targetBranch) {
        return git.locked(projectPath, () -> {
            Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
            String deployBranch = "jagt-deploy-" + sourceBranch;
            git.run(projectPath, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            if (Files.isDirectory(deployWorktree)) {
                if (hasDeployWorktree(projectPath, sourceBranch)) {
                    if (humanWorkWaiting(deployWorktree, targetBranch)) {
                        return finishDeploy(projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch);
                    }
                    discardDeployWorktree(projectPath, deployWorktree, deployBranch, targetBranch);
                } else if (git.worktreeOwner(deployWorktree).isPresent()) {
                    throw new ForeignDeployWorktreeException(deployWorktree, projectPath);
                } else {
                    clearEditorResidue(deployWorktree);
                }
            }
            // Deploy is decoupled from review state: its ONLY precondition is work that was SHIPPED. What it
            // merges is the remote branch, which is what the request shows — a session may push HEAD under the
            // task's name from a local branch named otherwise, leaving that local ref moved by nobody.
            String source = "origin/" + sourceBranch;
            if (git.run(projectPath,
                    List.of("git", "rev-parse", "--verify", "--quiet", source)).exitCode() != 0) {
                throw new NothingToDeployException("Nothing to deploy: branch '" + sourceBranch
                        + "' was never pushed — ship it first.");
            }
            String ahead = git.run(projectPath,
                            List.of("git", "rev-list", "--count", "origin/" + targetBranch + ".." + source))
                    .expectSuccess("git rev-list count " + source).stdout().trim();
            if ("0".equals(ahead)) {
                throw new NothingToDeployException(sourceBranch, targetBranch);
            }
            // A registration outlives a directory deleted by hand, and the add then refuses the branch as
            // still checked out.
            git.clearWorktreePath(projectPath, deployWorktree);
            git.run(projectPath, List.of("git", "worktree", "add",
                            "-B", deployBranch, deployWorktree.toString(), "origin/" + targetBranch))
                    .expectSuccess("git worktree add (deploy) " + targetBranch);
            // The message is explicit because git's default would name the throwaway branch, not the target.
            // --no-ff ALWAYS: that one merge commit is what `revert` undoes; a fast-forward leaves them loose.
            var merge = git.run(deployWorktree, List.of("git", "merge", "--no-ff",
                    "--no-edit", "-m", "Merge branch '" + sourceBranch + "' into " + targetBranch,
                    source));
            if (merge.exitCode() != 0) {
                String details = merge.stderr().isBlank() ? merge.stdout() : merge.stderr();
                // Only UNMERGED PATHS mean a conflict. git also exits non-zero for a missing committer identity
                // or a refusing hook, and calling those a conflict leaves a worktree the next deploy pushes.
                if (unmergedPaths(deployWorktree).isBlank()) {
                    removeDeployWorktree(projectPath, deployWorktree, deployBranch);
                    throw new IllegalStateException("Could not merge " + sourceBranch + " into " + targetBranch
                            + " — nothing was pushed and no conflict is waiting for you; git said: " + details);
                }
                throw new MergeConflictException(sourceBranch, targetBranch, details, deployWorktree);
            }
            return pushAndRemoveDeploy(projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch);
        });
    }

    /**
     * Whether the leftover worktree holds work of the human's: a resolution staged, committed, or part done. One
     * still exactly as the conflict left it, or one the merge was aborted in, holds none.
     */
    private boolean humanWorkWaiting(Path deployWorktree, String targetBranch) {
        if (!unmergedPaths(deployWorktree).isBlank()) {
            return partlyResolved(deployWorktree);
        }
        return mergeInProgress(deployWorktree) || resolutionCommitted(deployWorktree, targetBranch);
    }

    /**
     * Whether the conflict left in this task's deploy worktree is resolved in full: nothing unmerged, nothing
     * edited past what was staged, and the merge still standing or committed.
     */
    public boolean deployResolved(Path projectPath, String sourceBranch, String targetBranch) {
        Path deployWorktree = deployWorktreePath(projectPath, sourceBranch);
        return hasDeployWorktree(projectPath, sourceBranch) && unmergedPaths(deployWorktree).isBlank()
                && git.run(deployWorktree, List.of("git", "diff", "--quiet")).exitCode() == 0
                && (mergeInProgress(deployWorktree) || resolutionCommitted(deployWorktree, targetBranch));
    }

    /** A conflicted path already added is the human's work, and no press of theirs throws it away. */
    private boolean partlyResolved(Path deployWorktree) {
        return git.run(deployWorktree, List.of("git", "status", "--porcelain"))
                .expectSuccess("git status in " + deployWorktree).stdout().lines()
                .anyMatch(line -> !line.isBlank() && "MADRC".indexOf(line.charAt(0)) >= 0);
    }

    /**
     * A resolution committed by hand. The first parent must sit on the target: a worktree cut for a DIFFERENT
     * branch carries that branch's whole line, and pushing it would dump it into this one.
     */
    private boolean resolutionCommitted(Path deployWorktree, String targetBranch) {
        boolean ahead = !"0".equals(git.run(deployWorktree,
                        List.of("git", "rev-list", "--count", "origin/" + targetBranch + "..HEAD"))
                .expectSuccess("git rev-list count HEAD in " + deployWorktree).stdout().trim());
        return ahead && git.run(deployWorktree, List.of("git", "merge-base",
                "--is-ancestor", "HEAD^1", "origin/" + targetBranch)).exitCode() == 0;
    }

    private void discardDeployWorktree(Path projectPath, Path deployWorktree, String deployBranch,
                                       String targetBranch) {
        log.atInfo().setMessage("deploy worktree discarded")
                .addKeyValue("worktree", deployWorktree)
                .addKeyValue("cause", "no resolution staged, committed or part done in it")
                .addKeyValue("effect", "merged again from origin/" + targetBranch)
                .log();
        removeDeployWorktree(projectPath, deployWorktree, deployBranch);
    }

    private boolean mergeInProgress(Path deployWorktree) {
        return git.run(deployWorktree,
                List.of("git", "rev-parse", "-q", "--verify", "MERGE_HEAD")).exitCode() == 0;
    }

    private String finishDeploy(Path projectPath, Path deployWorktree, String deployBranch,
                                String sourceBranch, String targetBranch) {
        String unmerged = unmergedPaths(deployWorktree);
        if (!unmerged.isBlank()) {
            throw new MergeConflictException(sourceBranch, targetBranch,
                    "still unresolved (git add them):\n" + unmerged, deployWorktree);
        }
        if (mergeInProgress(deployWorktree)) {
            git.run(deployWorktree, List.of("git", "commit", "--no-edit"))
                    .expectSuccess("git commit (deploy resolution) " + deployWorktree);
        }
        return pushAndRemoveDeploy(projectPath, deployWorktree, deployBranch, sourceBranch, targetBranch);
    }

    /** KEEPS the worktree on a rejected push (the target moved under the merge) so the resolution isn't lost.
     *  Returns the pushed merge commit — what `revert` undoes, and knowable only before the worktree is gone. */
    private String pushAndRemoveDeploy(Path projectPath, Path deployWorktree, String deployBranch,
                                       String sourceBranch, String targetBranch) {
        var push = git.run(deployWorktree,
                List.of("git", "push", "origin", "HEAD:" + targetBranch));
        if (push.exitCode() != 0) {
            if (nothingLeftToPush(deployWorktree, targetBranch)) {
                removeDeployWorktree(projectPath, deployWorktree, deployBranch);
                throw new NothingToDeployException("Nothing left to push: what the deploy worktree held is"
                        + " already on " + targetBranch + ", which has moved on since. That worktree is gone —"
                        + " deploy again if '" + sourceBranch + "' still holds work " + targetBranch + " lacks.");
            }
            String d = push.stderr().isBlank() ? push.stdout() : push.stderr();
            throw new IllegalStateException("Deploy push to " + targetBranch + " was rejected. In " + deployWorktree
                    + " run `git merge origin/" + targetBranch + "`, resolve, then deploy again. Details: " + d);
        }
        String merged = git.run(deployWorktree, List.of("git", "rev-parse", "HEAD"))
                .expectSuccess("git rev-parse HEAD in " + deployWorktree).stdout().trim();
        removeDeployWorktree(projectPath, deployWorktree, deployBranch);
        return merged;
    }

    /**
     * Whether the worktree holds nothing the target lacks. The merge is NOT claimed as this task's: the commit
     * reached here may be the target tip the worktree was cut from.
     */
    private boolean nothingLeftToPush(Path deployWorktree, String targetBranch) {
        boolean fetched = git.run(deployWorktree, List.of("git", "fetch", "origin",
                "+refs/heads/" + targetBranch + ":refs/remotes/origin/" + targetBranch)).exitCode() == 0;
        return fetched && git.run(deployWorktree,
                List.of("git", "merge-base", "--is-ancestor", "HEAD", "origin/" + targetBranch)).exitCode() == 0;
    }

    private String unmergedPaths(Path worktree) {
        return git.run(worktree,
                        List.of("git", "diff", "--name-only", "--diff-filter=U"))
                .expectSuccess("git unmerged paths in " + worktree).stdout().trim();
    }

    /** Best-effort: the checkout is scaffolding, not state. */
    private void removeDeployWorktree(Path projectPath, Path deployWorktree, String deployBranch) {
        git.run(projectPath,
                List.of("git", "worktree", "remove", "--force", deployWorktree.toString()));
        git.run(projectPath, List.of("git", "branch", "-D", deployBranch));
    }

    /**
     * Undoes ONE deploy: reverts {@code mergeCommit} on {@code targetBranch} and pushes, returning the revert
     * commit. Only ever ADDS a commit — no rewrite, no force-push — and leaves the task branch alone. Refuses
     * rather than guess when the commit is not on the branch, is not a merge, or was already reverted; a revert
     * that conflicts is aborted and cleaned up, there being nothing useful for a human to finish there.
     */
    public String revertMergeAndPush(Path projectPath, String sourceBranch, String targetBranch,
                                     String mergeCommit) {
        return git.locked(projectPath, () -> {
            Path revertWorktree = revertWorktreePath(projectPath, sourceBranch);
            String revertBranch = "jagt-revert-" + sourceBranch;
            git.run(projectPath, List.of("git", "fetch", "--prune"))
                    .expectSuccess("git fetch in " + projectPath);
            if (Files.exists(revertWorktree)) {
                git.clearWorktreePath(projectPath, revertWorktree);
            }
            git.run(projectPath, List.of("git", "worktree", "add",
                            "-B", revertBranch, revertWorktree.toString(), "origin/" + targetBranch))
                    .expectSuccess("git worktree add (revert) " + targetBranch);
            try {
                requireRevertable(revertWorktree, targetBranch, mergeCommit);
                requireMergeCommit(revertWorktree, mergeCommit, targetBranch);
                // -m 1: a merge has two parents, and reverting it means "undo what the SECOND parent brought
                // in", i.e. keep the target branch's own line of history. Without it git refuses outright.
                var revert = git.run(revertWorktree, List.of("git", "revert",
                        "-m", "1", "--no-edit", mergeCommit));
                if (revert.exitCode() != 0) {
                    git.run(revertWorktree, List.of("git", "revert", "--abort"));
                    String details = revert.stderr().isBlank() ? revert.stdout() : revert.stderr();
                    throw new IllegalStateException("Cannot revert " + shortSha(mergeCommit) + " on "
                            + targetBranch + ": the revert conflicts with work done there since the deploy."
                            + " Decide what should survive and revert by hand: `git revert -m 1 "
                            + shortSha(mergeCommit) + "`. Details: " + details);
                }
                var push = git.run(revertWorktree,
                        List.of("git", "push", "origin", "HEAD:" + targetBranch));
                if (push.exitCode() != 0) {
                    String details = push.stderr().isBlank() ? push.stdout() : push.stderr();
                    throw new IllegalStateException("Revert push to " + targetBranch + " was rejected — it"
                            + " moved while the revert was being made. Try revert again. Details: " + details);
                }
                return git.run(revertWorktree, List.of("git", "rev-parse", "HEAD"))
                        .expectSuccess("git rev-parse HEAD in " + revertWorktree).stdout().trim();
            } finally {
                git.run(projectPath,
                        List.of("git", "worktree", "remove", "--force", revertWorktree.toString()));
                git.run(projectPath, List.of("git", "branch", "-D", revertBranch));
            }
        });
    }

    /** Only a merge reverts as ONE unit; a non-merge would undo one commit of a task instead of the deploy. */
    private void requireMergeCommit(Path revertWorktree, String mergeCommit, String targetBranch) {
        String parents = git.run(revertWorktree,
                        List.of("git", "rev-list", "--parents", "-n", "1", mergeCommit))
                .expectSuccess("git rev-list --parents " + mergeCommit).stdout().trim();
        if (parents.split("\\s+").length < 3) {
            throw new IllegalStateException("Commit " + shortSha(mergeCommit) + " on " + targetBranch
                    + " is not a merge, so reverting it would undo only part of what was deployed. Revert by"
                    + " hand after deciding what should come out. Nothing was reverted.");
        }
    }

    private void requireRevertable(Path revertWorktree, String targetBranch, String mergeCommit) {
        boolean onBranch = git.run(revertWorktree,
                List.of("git", "merge-base", "--is-ancestor", mergeCommit, "HEAD")).exitCode() == 0;
        if (!onBranch) {
            throw new IllegalStateException("Commit " + shortSha(mergeCommit) + " is not on " + targetBranch
                    + " — it was never deployed there, or that history was rewritten. Nothing was reverted.");
        }
        // git's own revert message is the marker, so a revert made by hand counts too.
        String existing = git.run(revertWorktree, List.of("git", "log",
                        "--fixed-strings", "--grep=This reverts commit " + mergeCommit, "--format=%H", "-1"))
                .expectSuccess("git log (revert search) in " + revertWorktree).stdout().trim();
        if (!existing.isBlank()) {
            throw new IllegalStateException("Commit " + shortSha(mergeCommit) + " was already reverted on "
                    + targetBranch + " by " + shortSha(existing) + ". Nothing to do.");
        }
    }

    public static String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
    }

    public static Path deployWorktreePath(Path projectPath, String sourceBranch) {
        return projectPath.toAbsolutePath().normalize().getParent()
                .resolve(TaskName.slug(sourceBranch) + "-deploy");
    }

    /**
     * Whether a deploy worktree for this task is waiting in THIS repository. The path is derived from the
     * repository's PARENT, so siblings derive the SAME one and finishing another's merge here would push to the
     * wrong remote.
     */
    public boolean hasDeployWorktree(Path projectPath, String sourceBranch) {
        return git.worktreeOwner(deployWorktreePath(projectPath, sourceBranch))
                .filter(owner -> GitCommands.sameDirectory(owner, projectPath))
                .isPresent();
    }

    /**
     * A path that is no checkout cannot be another repository's conflict: it is jagt's own leftover, an editor
     * having written its project files back into a directory git had emptied. That is deleted and the deploy goes
     * on; anything else found there is left untouched and named.
     */
    private void clearEditorResidue(Path path) {
        List<String> kept;
        try (var entries = Files.list(path)) {
            kept = entries.map(entry -> entry.getFileName().toString())
                    .filter(name -> !WorktreeNoise.EDITOR_RESIDUE.contains(name)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the deploy worktree path " + path, e);
        }
        if (!kept.isEmpty()) {
            throw new StaleDeployPathException(path, kept);
        }
        log.atInfo().setMessage("editor leftover deleted")
                .addKeyValue("path", path)
                .log();
        git.forceDeleteDir(path);
    }

    /** Where a revert is staged — separate from the deploy worktree, which may be sitting in a conflict. */
    public static Path revertWorktreePath(Path projectPath, String sourceBranch) {
        return projectPath.toAbsolutePath().normalize().getParent()
                .resolve(TaskName.slug(sourceBranch) + "-revert");
    }

    /**
     * The deploy worktree path this repository derives is occupied by a checkout it did not cut, so nothing was
     * attempted. Typed so a caller landing SEVERAL repositories can come back to this one once the path is free.
     */
    public static class StaleDeployPathException extends IllegalStateException {

        public StaleDeployPathException(Path deployWorktree, List<String> kept) {
            super("The deploy worktree path " + deployWorktree + " is not a checkout but holds "
                    + String.join(", ", kept) + ", so nothing can be deployed from there. Move or delete that"
                    + " directory, then deploy again.");
        }
    }

    public static class ForeignDeployWorktreeException extends IllegalStateException {

        public ForeignDeployWorktreeException(Path deployWorktree, Path projectPath) {
            super("The deploy worktree path " + deployWorktree + " holds a checkout "
                    + projectPath.getFileName() + " did not cut, so its merge cannot be finished here. Deal with"
                    + " that checkout first — finish its deploy, or `git worktree remove --force` it if it is"
                    + " stale.");
        }
    }

    /** The branch holds nothing the target does not already have; typed so a caller can tell it from a failure. */
    public static class NothingToDeployException extends IllegalStateException {

        public NothingToDeployException(String sourceBranch, String targetBranch) {
            super("Nothing to deploy: branch '" + sourceBranch + "' has no commits beyond " + targetBranch
                    + " (commit work first, or it is already deployed).");
        }

        NothingToDeployException(String message) {
            super(message);
        }
    }

    /**
     * A deploy merge hit conflicts. The checkout is LEFT at {@link #deployWorktree} for the human to resolve and
     * deploy again.
     */
    public static class MergeConflictException extends IllegalStateException {
        private final transient Path deployWorktree;

        public MergeConflictException(String sourceBranch, String targetBranch, String details, Path deployWorktree) {
            super("Merge CONFLICT merging " + sourceBranch + " into " + targetBranch + " — nothing was pushed."
                    + " Resolve it in the deploy worktree " + deployWorktree + " (this is the " + targetBranch
                    + " side; the " + sourceBranch + " branch and its review request are untouched): fix"
                    + " the conflicts, `git add` them, then deploy again to finish. Details: " + details);
            this.deployWorktree = deployWorktree;
        }

        public Path deployWorktree() {
            return deployWorktree;
        }
    }
}
