package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.flow.DeployStep;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.service.DeployTargets;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.jagt.orchestrator.service.DeployTargets.because;
import static dev.jagt.orchestrator.service.DeployTargets.names;

/**
 * Advances a deploy from where its record stands: one git step per repository, each fed to the reducer as the
 * {@link DeployStep} it was, until the press ends, a conflict is handed back or a repository cannot start.
 */
@Component
@RequiredArgsConstructor
public class DeployRunner {

    private final StateService stateService;
    private final DeployTargets deployTargets;
    private final GitDeploy gitDeploy;
    private final Clock clock;

    public void open(String taskId, List<Target> targets) {
        DeployStep opened = new DeployStep.Opened(targets.stream()
                .map(target -> Deployment.Target.of(target.project(), target.deployBranch())).toList());
        stateService.updateTask(taskId, t -> TaskReducer.deployStepped(t, opened, clock.millis()));
    }

    public Outcome advance(String taskId) {
        TaskState task = requireTask(taskId);
        List<Target> targets = deployTargets.all(task);
        int from = Objects.requireNonNull(task.deployment()).at();
        Landing landing = new Landing();
        for (int i = from; i < targets.size(); i++) {
            Outcome stopped = land(taskId, targets, i, landing);
            if (stopped != null) {
                return stopped;
            }
        }
        List<String> blocked = blocked(requireTask(taskId), from);
        if (!blocked.isEmpty()) {
            return notFinished(taskId, landing.merged, blocked, Objects.requireNonNull(landing.obstacle));
        }
        // Nothing landed and nothing resumed past means there was nothing to deploy at all.
        if (landing.merged.isEmpty() && landing.idle != null && from == 0) {
            throw landing.idle;
        }
        return deployed(taskId, requireTask(taskId), targets, from, landing.merged, landing.nothingToDo);
    }

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
    }

    private static final class Landing {
        final Map<String, String> merged = new LinkedHashMap<>();
        final List<String> nothingToDo = new ArrayList<>();
        GitDeploy.@Nullable NothingToDeployException idle;
        GitDeploy.@Nullable ForeignDeployWorktreeException obstacle;
    }

    private static List<String> blocked(TaskState task, int from) {
        List<Deployment.Target> recorded = Objects.requireNonNull(task.deployment()).targets();
        return recorded.subList(Math.min(from, recorded.size()), recorded.size()).stream()
                .filter(Deployment.Target::isBlocked).map(Deployment.Target::project).toList();
    }

    private @Nullable Outcome land(String taskId, List<Target> targets, int at, Landing landing) {
        Target target = targets.get(at);
        try {
            String commit = gitDeploy.mergeIntoAndPush(target.path(), taskId, target.deployBranch());
            landing.merged.put(target.project(), commit);
            boolean fix = gitDeploy.isFix(target.path(), commit);
            long now = clock.millis();
            stateService.updateTask(taskId, t -> TaskReducer.deployStepped(TaskReducer.deployStepped(t,
                    fix ? new DeployStep.Fixed(target.project(), commit) : new DeployStep.Merged(target.project(), commit),
                    now), new DeployStep.Pushed(target.project(), now), now));
        } catch (GitDeploy.NothingToDeployException e) {
            landing.nothingToDo.add(target.project());
            landing.idle = landing.idle == null ? e : landing.idle;
        } catch (GitDeploy.ForeignDeployWorktreeException e) {
            // The sibling holding the shared path is in this very list, so coming back beats refusing.
            stateService.updateTask(taskId, t -> TaskReducer.deployStepped(t,
                    new DeployStep.Blocked(target.project()), clock.millis()));
            landing.obstacle = landing.obstacle == null ? e : landing.obstacle;
        } catch (GitDeploy.MergeConflictException e) {
            return handBackConflict(taskId, targets, at, e);
        } catch (RuntimeException e) {
            return stoppedPartWay(taskId, targets, at, e);
        }
        return null;
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
     * Resolve on the DEPLOY side, never in the task branch: the request targets the base branch, so merging the
     * deploy branch into the task branch would balloon its diff with everything the deploy branch carries.
     */
    private Outcome handBackConflict(String taskId, List<Target> targets, int at,
                                    GitDeploy.MergeConflictException e) {
        Target conflicted = targets.get(at);
        stateService.updateTask(taskId, t -> TaskReducer.deployStepped(t,
                new DeployStep.Conflicted(conflicted.project(), Deployment.Cause.MERGE), clock.millis()));
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

    private Outcome deployed(String taskId, TaskState task, List<Target> targets, int from,
                            Map<String, String> merged, List<String> nothingToDo) {
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
        List<Deployment.Target> recorded = Objects.requireNonNull(task.deployment()).targets();
        List<String> earlier = targets.subList(0, from).stream()
                .map(target -> target.project() + " ("
                        + GitDeploy.shortSha(recorded.get(task.deployment().indexOf(target.project())).merge()) + ")")
                .toList();
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
