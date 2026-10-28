package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Objects;

/** Which of a task's requests git finds merged into their target, kept on the task for the flow to read. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MergedRequests {

    private final StateService stateService;
    private final ConfigService configService;
    private final WorktreeInspection inspection;

    /** Reads every request not yet found merged, records what git says, and answers whether none is left to ship. */
    public boolean refresh(String taskId) {
        TaskState task = stateService.task(taskId).orElse(null);
        if (task == null) {
            return false;
        }
        for (TaskRepo repo : task.repos()) {
            if (repo.hasReviewRequest() && !repo.wasMerged() && mergedNow(taskId, task, repo)) {
                String project = Objects.requireNonNull(repo.project());
                stateService.updateTask(taskId, current -> current.withMerged(project, true));
                log.atInfo().setMessage("request merged").addKeyValue("task", taskId)
                        .addKeyValue("project", project).log();
            }
        }
        return stateService.task(taskId).map(TaskState::requestsMerged).orElse(false);
    }

    private boolean mergedNow(String taskId, TaskState task, TaskRepo repo) {
        String project = Objects.requireNonNull(repo.project());
        try {
            return inspection.mergedIntoTarget(
                    Path.of(configService.project(project).path()).toAbsolutePath().normalize(),
                    Path.of(Objects.requireNonNull(repo.worktreePath())),
                    WorktreeChanges.targetBranch(task, configService.project(project).baseBranch()));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("merged read failed").addKeyValue("task", taskId)
                    .addKeyValue("project", project).addKeyValue("cause", e.toString()).log();
            return false;
        }
    }
}
