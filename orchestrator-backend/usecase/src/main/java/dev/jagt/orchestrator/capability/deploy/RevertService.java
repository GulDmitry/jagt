package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.capability.deploy.DeployTargets.Target;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.jagt.orchestrator.capability.deploy.DeployTargets.because;
import static dev.jagt.orchestrator.capability.deploy.DeployTargets.mergeCommit;
import static dev.jagt.orchestrator.capability.deploy.DeployTargets.names;

/** The undo of a deploy, the other write to a shared branch; it works from the deploy's other end. */
@Service
@RequiredArgsConstructor
public class RevertService {

    private final StateService stateService;
    private final DeployTargets deployTargets;
    private final GitDeploy gitDeploy;

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
    }

    /**
     * Undoes one deploy: reverts the merge commits it created on the deploy branches and pushes them. The task
     * branch keeps all its commits. Repositories are undone in reverse order and each success forgets its merge
     * commit, so a revert that fails part way can be repeated and touches only what is still live.
     */
    public Outcome revert(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        List<Target> landed = deployTargets.landed(task);
        Optional<Target> waiting = FlowRules.conflictedInTheDeployWorktree(task.status())
                ? deployTargets.stopped(task) : Optional.empty();
        if (landed.isEmpty()) {
            if (waiting.isPresent()) {
                return discarded(taskId, waiting.get());
            }
            throw unrecordedDeploy(taskId, deployTargets.all(task));
        }
        landed.forEach(DeployTargets::requireDeployable);
        List<String> reverted = new ArrayList<>();
        String lastRevertCommit = null;
        for (Target target : landed.reversed()) {
            try {
                lastRevertCommit = gitDeploy.revertMergeAndPush(target.path(), taskId, target.deployBranch(),
                        mergeCommit(task, target));
            } catch (RuntimeException e) {
                return stillLive(taskId, target, reverted, e);
            }
            stateService.updateTask(taskId, t -> t.withDeployCommit(target.project(), null));
            reverted.add(target.project() + " on " + target.deployBranch() + " ("
                    + GitDeploy.shortSha(lastRevertCommit) + ")");
        }
        // Only once all is out: a refused revert keeps the human's resolution.
        if (waiting.isPresent()) {
            gitDeploy.discardDeploy(waiting.get().path(), taskId);
        }
        return allReverted(taskId, task.repos().size() == 1, landed, reverted, lastRevertCommit);
    }

    private Outcome discarded(String taskId, Target at) {
        gitDeploy.discardDeploy(at.path(), taskId);
        String what = "discarded the conflicted merge into " + at.deployBranch() + ", nothing had landed";
        return Outcome.ok("revert " + taskId + ": " + what + "; REVERTED — fix and ship again, or `done`.", what);
    }

    private Outcome allReverted(String taskId, boolean singleRepo, List<Target> landed, List<String> reverted,
                               String revertCommit) {
        String tail = "; REVERTED — fix and ship again, or `done`.";
        if (singleRepo) {
            String on = "on " + landed.getFirst().deployBranch() + " (" + GitDeploy.shortSha(revertCommit) + ")";
            return Outcome.ok("Reverted " + taskId + " " + on + tail, "reverted " + on);
        }
        return Outcome.ok("revert " + taskId + ": reverted " + names(reverted) + tail,
                "reverted " + names(reverted));
    }

    /**
     * A revert that stopped part way. What came out is forgotten, so repeating undoes only the rest, but the task
     * stays DEPLOYED because something of it still is. Stamped as well as thrown: a console line is no record.
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

    /** Guessing the merge commit would risk reverting the WRONG merge on a shared branch. */
    private RuntimeException unrecordedDeploy(String taskId, List<Target> targets) {
        // Every repository, because a recipe naming one leaves the others live on their own branches.
        String where = targets.stream()
                .map(target -> "`git log --merges --grep " + taskId + " origin/" + target.deployBranch() + "`"
                        + (targets.size() > 1 ? " in " + target.project() : ""))
                .collect(Collectors.joining(", "));
        return new IllegalStateException("revert " + taskId + ": jagt records no merge commit of this task's —"
                + " nothing landed, or the deploy predates that being stored — and guessing on a shared branch is"
                + " not something it will do. If one is live, revert by hand: " + where
                + " to find the merge, then `git revert -m 1 <sha>` and push.");
    }
}
