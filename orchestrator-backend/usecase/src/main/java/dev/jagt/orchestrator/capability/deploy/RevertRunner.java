package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.flow.DeployStep;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.DeployTargets.Target;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static dev.jagt.orchestrator.service.DeployTargets.because;
import static dev.jagt.orchestrator.service.DeployTargets.names;

/**
 * Takes a deploy out from its other end: the repositories whose record holds a merge, last first, the fixes on top
 * of each before its merge. Each success is a {@link DeployStep.Reverted}, so a revert that fails part way can be
 * repeated and touches only what is still live.
 */
@Component
@RequiredArgsConstructor
public class RevertRunner {

    private final StateService stateService;
    private final GitDeploy gitDeploy;
    private final Clock clock;

    public Outcome advance(String taskId, TaskState task, List<Target> landed, Optional<Target> waiting) {
        List<String> reverted = new ArrayList<>();
        @Nullable String lastRevertCommit = null;
        for (Target target : landed.reversed()) {
            Deployment.Target recorded = recorded(task, target);
            try {
                lastRevertCommit = gitDeploy.revertMergeAndPush(target.path(), taskId, target.deployBranch(),
                        Objects.requireNonNull(recorded.merge()), recorded.fixes().reversed());
            } catch (GitDeploy.RevertConflictException e) {
                return handBackConflict(taskId, target, reverted, e);
            } catch (RuntimeException e) {
                return stillLive(taskId, target, reverted, e);
            }
            DeployStep done = new DeployStep.Reverted(target.project(), lastRevertCommit);
            stateService.updateTask(taskId, t -> TaskReducer.deployStepped(t, done, 0));
            reverted.add(target.project() + " on " + target.deployBranch() + " ("
                    + GitDeploy.shortSha(lastRevertCommit) + ")");
        }
        // Only once all is out: a refused revert keeps the human's resolution.
        waiting.ifPresent(at -> discardDeploy(taskId, at));
        return allReverted(taskId, task.repos().size() == 1, landed, reverted, lastRevertCommit);
    }

    /** A conflicted merge with nothing landed before it: the worktree is all there is to take out. */
    public Outcome discard(String taskId, Target at) {
        discardDeploy(taskId, at);
        String what = "discarded the conflicted merge into " + at.deployBranch() + ", nothing had landed";
        return Outcome.ok("revert " + taskId + ": " + what + "; REVERTED — fix and ship again, or `done`.", what);
    }

    private void discardDeploy(String taskId, Target at) {
        gitDeploy.discardDeploy(at.path(), taskId);
        stateService.updateTask(taskId, t -> TaskReducer.deployStepped(t, new DeployStep.Discarded(at.project()), 0));
    }

    private static Deployment.Target recorded(TaskState task, Target target) {
        Deployment deployment = Objects.requireNonNull(task.deployment());
        return deployment.targets().get(deployment.indexOf(target.project()));
    }

    private Outcome allReverted(String taskId, boolean singleRepo, List<Target> landed, List<String> reverted,
                               @Nullable String revertCommit) {
        String tail = "; REVERTED — fix and ship again, or `done`.";
        if (singleRepo) {
            String on = "on " + landed.getFirst().deployBranch() + " (" + GitDeploy.shortSha(revertCommit) + ")";
            return Outcome.ok("Reverted " + taskId + " " + on + tail, "reverted " + on);
        }
        return Outcome.ok("revert " + taskId + ": reverted " + names(reverted) + tail,
                "reverted " + names(reverted));
    }

    /** The conflict stays in the revert worktree: what the session resolves there, the next revert finishes. */
    private Outcome handBackConflict(String taskId, Target at, List<String> reverted,
                                     GitDeploy.RevertConflictException e) {
        stateService.updateTask(taskId, t -> TaskReducer.deployStepped(t,
                new DeployStep.Conflicted(at.project(), Deployment.Cause.REVERT), clock.millis()));
        String done = reverted.isEmpty() ? "" : " Already reverted: " + names(reverted) + ".";
        return Outcome.conflict("revert " + taskId + ": CONFLICT reverting " + at.project() + " on "
                + at.deployBranch() + ", nothing pushed for it." + done + " Resolve in " + e.revertWorktree()
                + " (`git add`), then `revert " + taskId + "` again.",
                "resolve revert conflict in " + e.revertWorktree());
    }

    /**
     * A revert that stopped part way. What came out is forgotten, so repeating undoes only the rest, but the task
     * is not REVERTED because something of it still is. Stamped as well as thrown: a console line is no record.
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
}
