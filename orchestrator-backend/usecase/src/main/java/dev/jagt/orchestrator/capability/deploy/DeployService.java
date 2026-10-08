package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.capability.deploy.DeployTargets.Target;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.port.EditorDriver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.jagt.orchestrator.capability.deploy.DeployTargets.because;
import static dev.jagt.orchestrator.capability.deploy.DeployTargets.mergeCommit;
import static dev.jagt.orchestrator.capability.deploy.DeployTargets.names;

/**
 * One of the only two operations that write a SHARED branch; it does not check that the caller is the human, that
 * gate sits outside. A task spanning repositories lands one at a time and stops at the first conflict: a shared
 * branch cannot be written atomically, so the half-state is reported.
 */
@Service
@RequiredArgsConstructor
public class DeployService {

    private final StateService stateService;
    private final DeployTargets deployTargets;
    private final GitDeploy gitDeploy;
    private final EditorDriver editorDriver;

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
    }

    public Outcome deploy(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        List<Target> targets = deployTargets.all(task);
        targets.forEach(DeployTargets::requireDeployable);
        Map<String, String> merged = new LinkedHashMap<>();
        List<String> nothingToDo = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        GitDeploy.NothingToDeployException idle = null;
        GitDeploy.ForeignDeployWorktreeException obstacle = null;
        int from = resumeFrom(task, taskId, targets);
        for (int i = from; i < targets.size(); i++) {
            Target target = targets.get(i);
            try {
                String commit = gitDeploy.mergeIntoAndPush(target.path(), taskId, target.deployBranch());
                merged.put(target.project(), commit);
                stateService.updateTask(taskId, t -> t.withDeployCommit(target.project(), commit));
            } catch (GitDeploy.NothingToDeployException e) {
                nothingToDo.add(target.project());
                idle = idle == null ? e : idle;
                continue;
            } catch (GitDeploy.ForeignDeployWorktreeException e) {
                // The sibling holding the shared path is in this very list, so coming back beats refusing.
                blocked.add(target.project());
                obstacle = obstacle == null ? e : obstacle;
                continue;
            } catch (GitDeploy.MergeConflictException e) {
                return handBackConflict(taskId, targets, i, e);
            } catch (RuntimeException e) {
                return stoppedPartWay(taskId, targets, i, e);
            }
            // A human who opened the worktree to resolve a conflict is left with a dead editor entry otherwise.
            editorDriver.forgetProject(GitDeploy.deployWorktreePath(target.path(), taskId));
        }
        if (!blocked.isEmpty()) {
            return notFinished(taskId, merged, blocked, obstacle);
        }
        // Nothing landed and nothing resumed past means there was nothing to deploy at all.
        if (merged.isEmpty() && idle != null && from == 0) {
            throw idle;
        }
        return deployed(taskId, targets, from, merged, nothingToDo);
    }

    /**
     * At least one repository never started, so the task is NOT deployed. A repeat is advised only where something
     * DID land: the holder of the shared path is then a sibling that has just released it. With nothing landed
     * nothing was released either, so the obstacle itself is what the human is told.
     */
    private Outcome notFinished(String taskId, Map<String, String> merged, List<String> blocked,
                                GitDeploy.ForeignDeployWorktreeException obstacle) {
        List<String> landed = List.copyOf(merged.keySet());
        String next = landed.isEmpty()
                ? " never started: " + obstacle.getMessage()
                : " could not start while the shared deploy worktree path held another repository's checkout."
                        + " Run `deploy " + taskId + "` again.";
        return Outcome.partial("deploy " + taskId + ": merged " + names(landed) + ", but " + names(blocked) + next,
                "deploy stopped part way — live on the deploy branch: " + names(landed)
                        + ". NOT deployed: " + names(blocked) + ".", null);
    }

    /**
     * Where a repeated deploy picks the sequence up. Only a task HANDED BACK from a conflict has one; a worktree
     * left over from any other round is not a resume point, and jumping to it would skip the ones before it.
     */
    private int resumeFrom(TaskState task, String taskId, List<Target> targets) {
        return targets.size() == 1 ? 0 : deployTargets.stopped(task, taskId).map(targets::indexOf).orElse(0);
    }

    /**
     * Resolve on the DEPLOY side, never in the task branch: the request targets the base branch, so merging the
     * deploy branch into the task branch would balloon its diff with everything the deploy branch carries.
     */
    private Outcome handBackConflict(String taskId, List<Target> targets, int at,
                                    GitDeploy.MergeConflictException e) {
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
     * Both sides of a part-way deploy, named. Read from WHERE the sequence stopped rather than from the recorded
     * merge commits: those outlive the round that made them, so after a second ship every repository reads as live.
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
                    + GitDeploy.shortSha(merged.get(only.project())) + "); DEPLOYED", stamp);
        }
        List<String> landed = targets.stream().filter(target -> merged.containsKey(target.project()))
                .map(target -> target.project() + " into " + target.deployBranch()
                        + " (" + GitDeploy.shortSha(merged.get(target.project())) + ")").toList();
        // A resumed sequence merged only its tail; the whole picture is what the human is owed.
        List<String> earlier = targets.subList(0, from).stream()
                .map(target -> target.project() + " (" + GitDeploy.shortSha(mergeCommit(task, target)) + ")").toList();
        String already = earlier.isEmpty() ? "" : ", already on the deploy branch: " + names(earlier);
        String idle = nothingToDo.isEmpty() ? "" : ", nothing to deploy in " + names(nothingToDo);
        return Outcome.ok("deploy " + taskId + ": merged " + names(landed) + already + idle + "; DEPLOYED",
                stamp);
    }

    /**
     * A deploy that broke off for a reason no resolution is waiting on. The status is left alone, there being
     * nothing to resolve in a worktree; the message is stamped anyway, a console line being no record.
     */
    private Outcome stoppedPartWay(String taskId, List<Target> targets, int at, RuntimeException cause) {
        if (targets.size() == 1 || at == 0) {
            throw cause;
        }
        String half = halfState(targets, at);
        return Outcome.partial("deploy " + taskId + " stopped part way. " + half + because(cause),
                "deploy stopped part way — " + half, cause);
    }

    private static List<String> deployBranches(List<Target> targets) {
        Set<String> branches = new LinkedHashSet<>(targets.stream().map(Target::deployBranch).toList());
        return List.copyOf(branches);
    }
}
