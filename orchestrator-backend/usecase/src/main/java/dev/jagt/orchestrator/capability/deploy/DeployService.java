package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.service.GitService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.port.EditorDriver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The only two operations that write a SHARED branch: merging a task into the deploy branch, and taking that
 * merge back out. Neither checks that the caller is the human; that gate sits outside.
 *
 * <p>A task spanning repositories lands one at a time, in the order it holds them, and stops at the first
 * conflict: a shared branch cannot be written atomically anyway, so the half-state is reported rather than
 * pretended away. The undo works from the other end.
 */
@Service
@RequiredArgsConstructor
public class DeployService {

    private final StateService stateService;
    private final ConfigService configService;
    private final GitService gitService;
    private final EditorDriver editorDriver;

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " not found in state.json"));
    }

    public Outcome deploy(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        List<Target> targets = deployTargets(task);
        targets.forEach(DeployService::requireDeployable);
        Map<String, String> merged = new LinkedHashMap<>();
        List<String> nothingToDo = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        GitService.NothingToDeployException idle = null;
        int from = resumeFrom(task, taskId, targets);
        for (int i = from; i < targets.size(); i++) {
            Target target = targets.get(i);
            try {
                String commit = gitService.mergeIntoAndPush(target.path(), taskId, target.deployBranch());
                merged.put(target.project(), commit);
                stateService.updateTask(taskId, t -> t.withDeployCommit(target.project(), commit));
            } catch (GitService.NothingToDeployException e) {
                nothingToDo.add(target.project());
                idle = idle == null ? e : idle;
                continue;
            } catch (GitService.ForeignDeployWorktreeException e) {
                // Coming back to this repository beats refusing the whole sequence — the sibling holding the
                // shared path is in this very list.
                blocked.add(target.project());
                continue;
            } catch (GitService.MergeConflictException e) {
                return handBackConflict(taskId, targets, i, e);
            } catch (RuntimeException e) {
                return stoppedPartWay(taskId, targets, i, e);
            }
            // A human who opened the worktree to resolve a conflict would otherwise be left with a dead entry
            // in the editor's project list.
            editorDriver.forgetProject(GitService.deployWorktreePath(target.path(), taskId));
        }
        if (!blocked.isEmpty()) {
            return notFinished(taskId, merged, blocked);
        }
        // Nothing landed and nothing resumed past means there was nothing to deploy at all; a RESUMED sequence
        // whose remainder is all idle is finished.
        if (merged.isEmpty() && idle != null && from == 0) {
            throw idle;
        }
        return deployed(taskId, targets, from, merged, nothingToDo);
    }

    /** Some repositories landed and at least one never started, so the task is NOT deployed and deploy repeats. */
    private Outcome notFinished(String taskId, Map<String, String> merged, List<String> blocked) {
        List<String> landed = List.copyOf(merged.keySet());
        return Outcome.partial("deploy " + taskId + ": merged " + names(landed)
                + ", but " + names(blocked) + " could not start while the shared deploy worktree path held"
                + " another repository's checkout. Run `deploy " + taskId + "` again.",
                "deploy stopped part way — live on the deploy branch: " + names(landed)
                        + ". NOT deployed: " + names(blocked) + ".", null);
    }

    /**
     * Where a repeated deploy picks the sequence up. Only a task HANDED BACK from a conflict has one: that is the
     * state whose deploy worktree is still on disk, and everything before it in the list has been dealt with. A
     * worktree left over from any other round is not a resume point — jumping to it would silently skip the
     * repositories before it and still call the task deployed.
     */
    private int resumeFrom(TaskState task, String taskId, List<Target> targets) {
        if (targets.size() == 1 || task.status() != TaskStatus.DEPLOY_CONFLICT) {
            return 0;
        }
        for (int i = 0; i < targets.size(); i++) {
            if (gitService.hasDeployWorktree(targets.get(i).path(), taskId)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Resolve on the DEPLOY side, never in the task branch: the request targets the base branch, so merging the
     * deploy branch into the task branch would balloon its diff with everything the deploy branch carries.
     */
    private Outcome handBackConflict(String taskId, List<Target> targets, int at,
                                    GitService.MergeConflictException e) {
        Target conflicted = targets.get(at);
        String half = targets.size() > 1 ? halfState(targets, at) : "";
        String note = half.isEmpty() ? "" : " — " + half;
        String what = targets.size() > 1
                ? "CONFLICT merging " + conflicted.project() + " into " + conflicted.deployBranch() + ". " + half
                : "CONFLICT into " + conflicted.deployBranch() + ", nothing pushed.";
        return Outcome.conflict("deploy " + taskId + ": " + what + " Resolve in " + e.deployWorktree()
                + " (`git add`), then `deploy " + taskId + "` again.",
                "resolve conflict in " + e.deployWorktree() + note);
    }

    /**
     * Both sides of a part-way deploy, named — the only place a human learns the task is half live on a shared
     * branch, so neither list may be left implied. Read from WHERE the sequence stopped rather than from the
     * recorded merge commits: those outlive the round that made them, so after a second ship every repository
     * would read as live.
     */
    private String halfState(List<Target> targets, int stoppedAt) {
        List<String> live = targets.subList(0, stoppedAt).stream().map(Target::project).toList();
        List<String> pending = targets.subList(stoppedAt, targets.size()).stream().map(Target::project).toList();
        return "Live on the deploy branch: " + names(live) + ". NOT deployed: " + names(pending) + ".";
    }

    private Outcome deployed(String taskId, List<Target> targets, int from, Map<String, String> merged,
                            List<String> nothingToDo) {
        TaskState task = requireTask(taskId);
        String stamp = "deployed to " + names(deployBranches(targets));
        if (targets.size() == 1) {
            Target only = targets.getFirst();
            return Outcome.ok("Merged " + taskId + " into " + only.deployBranch() + " ("
                    + shortSha(merged.get(only.project())) + "); DEPLOYED", stamp);
        }
        List<String> landed = targets.stream().filter(target -> merged.containsKey(target.project()))
                .map(target -> target.project() + " into " + target.deployBranch()
                        + " (" + shortSha(merged.get(target.project())) + ")").toList();
        // A resumed sequence merged only its tail, and the human is owed the whole picture rather than the part
        // this call happened to do.
        List<String> earlier = targets.subList(0, from).stream()
                .map(target -> target.project() + " (" + shortSha(mergeCommit(task, target)) + ")").toList();
        String already = earlier.isEmpty() ? "" : ", already on the deploy branch: " + names(earlier);
        String idle = nothingToDo.isEmpty() ? "" : ", nothing to deploy in " + names(nothingToDo);
        return Outcome.ok("deploy " + taskId + ": merged " + names(landed) + already + idle + "; DEPLOYED",
                stamp);
    }

    /**
     * A deploy that broke off for a reason no resolution is waiting on — a rejected push, a fetch that failed.
     * The status is left alone, because there is nothing for a human to resolve in a worktree; the message is
     * stamped anyway, or a repository stays live on a shared branch with nothing but a console line admitting it.
     */
    private Outcome stoppedPartWay(String taskId, List<Target> targets, int at, RuntimeException cause) {
        if (targets.size() == 1 || at == 0) {
            throw cause;
        }
        String half = halfState(targets, at);
        return Outcome.partial("deploy " + taskId + " stopped part way. " + half + because(cause),
                "deploy stopped part way — " + half, cause);
    }

    /** A cause without a message must not end the report in the word "null". */
    private static String because(RuntimeException cause) {
        return cause.getMessage() == null ? "" : " " + cause.getMessage();
    }

    /**
     * Undoes one deploy: reverts the merge commits it created on the deploy branches and pushes them. The task
     * branch keeps all its commits, so the normal follow-up is "fix and ship again".
     *
     * <p>Repositories are undone in reverse order, and each one that succeeds forgets its merge commit — so a
     * revert that fails part way can be repeated and touches only what is still live. A repository that never
     * landed is nothing to undo rather than an error.
     */
    public Outcome revert(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        List<Target> landed = landedTargets(task);
        if (landed.isEmpty()) {
            throw unrecordedDeploy(taskId, deployTargets(task));
        }
        landed.forEach(DeployService::requireDeployable);
        List<String> reverted = new ArrayList<>();
        String lastRevertCommit = null;
        for (Target target : landed.reversed()) {
            try {
                lastRevertCommit = gitService.revertMergeAndPush(target.path(), taskId, target.deployBranch(),
                        mergeCommit(task, target));
            } catch (RuntimeException e) {
                return stillLive(taskId, target, reverted, e);
            }
            stateService.updateTask(taskId, t -> t.withDeployCommit(target.project(), null));
            reverted.add(target.project() + " on " + target.deployBranch() + " ("
                    + shortSha(lastRevertCommit) + ")");
        }
        return allReverted(taskId, task.repos().size() == 1, landed, reverted, lastRevertCommit);
    }

    private Outcome allReverted(String taskId, boolean singleRepo, List<Target> landed, List<String> reverted,
                               String revertCommit) {
        String tail = "; REVERTED — fix and ship again, or `done`.";
        if (singleRepo) {
            String on = "on " + landed.getFirst().deployBranch() + " (" + shortSha(revertCommit) + ")";
            return Outcome.ok("Reverted " + taskId + " " + on + tail, "reverted " + on);
        }
        return Outcome.ok("revert " + taskId + ": reverted " + names(reverted) + tail,
                "reverted " + names(reverted));
    }

    /**
     * A revert that stopped part way. What already came out has been forgotten, so repeating the verb undoes
     * only the rest — but the task stays DEPLOYED, because something of it still is. The message is stamped as
     * well as thrown: a sentence in a console the human has since scrolled past is not a record of a shared
     * branch holding half a change.
     */
    private Outcome stillLive(String taskId, Target at, List<String> reverted, RuntimeException cause) {
        if (reverted.isEmpty()) {
            throw cause;
        }
        String half = "reverted " + names(reverted) + ", " + at.project() + " still live on "
                + at.deployBranch();
        return Outcome.partial("revert " + taskId + ": " + half + " — repeat `revert " + taskId
                + "` once this is dealt with." + because(cause), half, cause);
    }

    /**
     * Guessing the merge commit (searching the log by branch name) would risk reverting the WRONG merge on a
     * shared branch — the one mistake with no cheap undo.
     */
    private RuntimeException unrecordedDeploy(String taskId, List<Target> targets) {
        // Every repository, because a recipe naming one leaves the others live on their own branches.
        String where = targets.stream()
                .map(target -> "`git log --merges --grep " + taskId + " origin/" + target.deployBranch() + "`"
                        + (targets.size() > 1 ? " in " + target.project() : ""))
                .collect(Collectors.joining(", "));
        return new IllegalStateException("revert " + taskId + ": jagt has no record of which commit this"
                + " deploy created (it predates that being stored), and guessing on a shared branch is not"
                + " something it will do. Revert by hand: " + where
                + " to find the merge, then `git revert -m 1 <sha>` and push.");
    }

    /**
     * Every repository the task works in, paired with where it lands. All of them are resolved before anything is
     * pushed, so a project misconfigured at the end of the list cannot be discovered half way through.
     */
    private List<Target> deployTargets(TaskState task) {
        List<Target> targets = new ArrayList<>();
        for (TaskRepo repo : task.repos()) {
            targets.add(new Target(repo.project(), configService.project(repo.project())));
        }
        return targets;
    }

    /**
     * The repositories an undo has something to take out, and ONLY those get resolved: a repository that never
     * landed must not stand between a human and the merge that is live on a shared branch, whatever became of its
     * project in the configuration since.
     */
    private List<Target> landedTargets(TaskState task) {
        List<Target> landed = new ArrayList<>();
        for (TaskRepo repo : task.repos()) {
            if (repo.deployCommit() != null && !repo.deployCommit().isBlank()) {
                landed.add(new Target(repo.project(), configService.project(repo.project())));
            }
        }
        return landed;
    }

    /**
     * HARD SAFETY: the deploy branch must NEVER be the base/release branch tasks are cut from — jagt writes to
     * exactly one shared branch per repository and it is not that one.
     */
    private static void requireDeployable(Target target) {
        ProjectConfig project = target.config();
        if (project.deployBranch() == null || project.deployBranch().isBlank()) {
            throw new IllegalArgumentException("Project '" + target.project()
                    + "' has no deployBranch in config.json — set it to enable deploy");
        }
        if (project.deploysIntoTheBaseBranch()) {
            throw new IllegalArgumentException("REFUSED: deployBranch equals the base branch '"
                    + project.baseBranchName()
                    + "'. jagt must never merge into the branch tasks are created from — point deployBranch"
                    + " at a downstream branch (e.g. dev).");
        }
    }

    private static String mergeCommit(TaskState task, Target target) {
        String commit = task.repo(target.project()).map(TaskRepo::deployCommit).orElse(null);
        return commit == null || commit.isBlank() ? null : commit;
    }

    private static List<String> deployBranches(List<Target> targets) {
        Set<String> branches = new LinkedHashSet<>(targets.stream().map(Target::deployBranch).toList());
        return List.copyOf(branches);
    }

    private static String names(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
    }

    private record Target(String project, ProjectConfig config) {

        Path path() {
            return Path.of(config.path());
        }

        String deployBranch() {
            return config.deployBranch();
        }
    }
}
