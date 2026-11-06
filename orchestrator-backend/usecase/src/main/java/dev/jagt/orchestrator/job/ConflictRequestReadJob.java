package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.DeployTargets;
import dev.jagt.orchestrator.service.MergedRequests;
import dev.jagt.orchestrator.service.ReviewReader;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Reads the request of the repository a deploy was handed back on for its pipeline or its merge, for as long as the hand-back stands. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictRequestReadJob implements TaskJob {

    private final ReviewReader reviewReader;
    private final MergedRequests mergedRequests;

    @Override
    public String id() {
        return "conflict-request-read";
    }

    @Override
    public String describe() {
        return "read the request of the repository a deploy waits on for its pipeline or its merge, and take it out of the task once the host closed it";
    }

    @Override
    public Duration every() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStatuses(FlowRules::conflictedInTheDeployWorktree).withStops(StopKind.withoutPause());
    }

    @Override
    public void run(String taskId, TaskState task) {
        if (task.repos().size() > 1) {
            conflictedRepo(task).ifPresent(repo -> read(taskId, repo));
        }
    }

    private static Optional<TaskRepo> conflictedRepo(TaskState task) {
        if (DeployTargets.conflictCause(task).filter(cause -> cause != Deployment.Cause.REVERT).isEmpty()) {
            return Optional.empty();
        }
        return Objects.requireNonNull(task.deployment()).conflictedTarget().map(Deployment.Target::project)
                .flatMap(project -> task.repos().stream()
                        .filter(repo -> project.equals(repo.project()) && repo.hasReviewRequest()).findFirst());
    }

    private void read(String taskId, TaskRepo repo) {
        try {
            reviewReader.read(taskId, Objects.requireNonNull(repo.mrUrl())).facts()
                    .filter(facts -> facts.exists() && facts.closed())
                    .ifPresent(closed -> mergedRequests.hostSaidClosed(taskId, repo));
        } catch (RuntimeException failed) {
            log.atWarn().setMessage("request of the conflicted repository not read").addKeyValue("task", taskId)
                    .addKeyValue("project", repo.project()).addKeyValue("cause", failed.toString())
                    .addKeyValue("effect", "read again in a minute").log();
        }
    }
}
