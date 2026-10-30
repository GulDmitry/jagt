package dev.jagt.orchestrator.capability.deploy;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.DeployTargets.Target;
import dev.jagt.orchestrator.flow.Outcome;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


/** The undo of a deploy, the other write to a shared branch; it works from the deploy's other end. */
@Service
@RequiredArgsConstructor
public class RevertService {

    private final StateService stateService;
    private final DeployTargets deployTargets;
    private final RevertRunner runner;

    private TaskState requireTask(String taskId) {
        return stateService.task(taskId)
                .orElseThrow(() -> Refusal.noSuchTask(taskId));
    }

    /**
     * Undoes one deploy: reverts the merge commits it created on the deploy branches and pushes them. The task
     * branch keeps all its commits.
     */
    public Outcome revert(String taskId) {
        taskId = stateService.canonicalTaskId(taskId);
        TaskState task = requireTask(taskId);
        List<Target> landed = deployTargets.landed(task);
        Optional<Target> waiting = deployTargets.stopped(task);
        if (landed.isEmpty()) {
            if (waiting.isPresent()) {
                return runner.discard(taskId, waiting.get());
            }
            throw unrecordedDeploy(taskId, task);
        }
        landed.forEach(DeployTargets::requireDeployable);
        return runner.advance(taskId, task, landed, waiting);
    }

    /** Guessing the merge commit would risk reverting the WRONG merge on a shared branch. */
    private RuntimeException unrecordedDeploy(String taskId, TaskState task) {
        // Every repository, because a recipe naming one leaves the others live on their own branches.
        List<TaskRepo> repos = task.repos();
        String where = repos.stream()
                .map(repo -> "`git log --merges --grep " + taskId + " origin/"
                        + deployTargets.deployBranch(Objects.requireNonNull(repo.project())).orElse("<its deploy branch>") + "`"
                        + (repos.size() > 1 ? " in " + repo.project() : ""))
                .collect(Collectors.joining(", "));
        return new IllegalStateException("revert " + taskId + ": jagt records no merge commit of this task's —"
                + " nothing landed, or the deploy predates that being stored — and guessing on a shared branch is"
                + " not something it will do. If one is live, revert by hand: " + where
                + " to find the merge, then `git revert -m 1 <sha>` and push.");
    }
}
