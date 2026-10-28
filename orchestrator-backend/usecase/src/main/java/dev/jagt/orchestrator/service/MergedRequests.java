package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /** The commit each request's branch last had pushed, one per repository; blank where the repository cannot name every one. */
    public String pushedTips(String taskId) {
        List<String> tips = new ArrayList<>();
        for (TaskRepo repo : stateService.task(taskId).map(TaskState::repos).orElse(List.of())) {
            if (!repo.hasReviewRequest()) {
                continue;
            }
            String project = Objects.requireNonNull(repo.project());
            Optional<String> tip = Optional.empty();
            try {
                tip = inspection.pushedTip(projectPath(project), Path.of(Objects.requireNonNull(repo.worktreePath())));
            } catch (RuntimeException e) {
                log.atWarn().setMessage("pushed tip read failed").addKeyValue("task", taskId)
                        .addKeyValue("project", project).addKeyValue("cause", e.toString()).log();
            }
            if (tip.isEmpty()) {
                return "";
            }
            tips.add(project + "=" + tip.get());
        }
        return String.join(",", tips);
    }

    private Path projectPath(String project) {
        return Path.of(configService.project(project).path()).toAbsolutePath().normalize();
    }

    private boolean mergedNow(String taskId, TaskState task, TaskRepo repo) {
        String project = Objects.requireNonNull(repo.project());
        try {
            return inspection.mergedIntoTarget(
                    projectPath(project),
                    Path.of(Objects.requireNonNull(repo.worktreePath())),
                    WorktreeChanges.targetBranch(task, configService.project(project).baseBranch()));
        } catch (RuntimeException e) {
            log.atWarn().setMessage("merged read failed").addKeyValue("task", taskId)
                    .addKeyValue("project", project).addKeyValue("cause", e.toString()).log();
            return false;
        }
    }
}
