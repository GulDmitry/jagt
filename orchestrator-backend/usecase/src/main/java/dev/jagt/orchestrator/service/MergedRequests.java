package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Which of a task's requests git finds merged into their target, kept on the task for the flow to read. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MergedRequests {

    private final StateService stateService;
    private final ConfigService configService;
    private final WorktreeInspection inspection;
    private final WorktreeChanges worktreeChanges;

    /**
     * Reads every request the host has not said merged, records what git says, merged or not, and answers whether
     * none is left to ship. A read git could not answer leaves the record as it is.
     */
    public boolean refresh(String taskId) {
        TaskState task = stateService.task(taskId).orElse(null);
        if (task == null) {
            return false;
        }
        for (TaskRepo repo : task.repos()) {
            if (repo.hasReviewRequest() && !repo.wasHostMerged()) {
                mergedNow(taskId, task, repo).filter(now -> now != repo.wasMerged())
                        .ifPresent(now -> record(taskId, Objects.requireNonNull(repo.project()), now));
            }
        }
        return stateService.task(taskId).map(TaskState::requestsMerged).orElse(false);
    }

    /** The host said {@code project}'s request is merged, which git cannot see for a squash or a fast-forward; its word outlasts git's. */
    public void hostSaidMerged(String taskId, String project) {
        boolean known = stateService.task(taskId).map(task -> task.repos().stream()
                .anyMatch(repo -> project.equals(repo.project()) && repo.wasHostMerged())).orElse(true);
        if (!known) {
            stateService.updateTask(taskId, current -> current.withMergedByHost(project));
            log.atInfo().setMessage("request merged").addKeyValue("task", taskId)
                    .addKeyValue("project", project).addKeyValue("by", "host").log();
        }
    }

    private void record(String taskId, String project, boolean merged) {
        stateService.updateTask(taskId, current -> current.withMerged(project, merged));
        log.atInfo().setMessage(merged ? "request merged" : "request no longer merged")
                .addKeyValue("task", taskId).addKeyValue("project", project).addKeyValue("by", "git").log();
    }

    /** The commit each request's branch last had pushed, one per repository; blank where the repository cannot name every one. */
    public String pushedTips(String taskId) {
        return stateService.task(taskId).map(worktreeChanges::pushedTips).orElse("");
    }

    private Path projectPath(String project) {
        return Path.of(configService.project(project).path()).toAbsolutePath().normalize();
    }

    private Optional<Boolean> mergedNow(String taskId, TaskState task, TaskRepo repo) {
        String project = Objects.requireNonNull(repo.project());
        try {
            return Optional.of(inspection.mergedIntoTarget(
                    projectPath(project),
                    Path.of(Objects.requireNonNull(repo.worktreePath())),
                    WorktreeChanges.targetBranch(task, configService.project(project).baseBranch())));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("merged read failed").addKeyValue("task", taskId)
                    .addKeyValue("project", project).addKeyValue("cause", e.toString()).log();
            return Optional.empty();
        }
    }
}
