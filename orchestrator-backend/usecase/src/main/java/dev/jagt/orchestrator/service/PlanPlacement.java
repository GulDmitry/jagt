package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.protocol.PlanRepositories;
import dev.jagt.orchestrator.task.Artifact;
import dev.jagt.orchestrator.task.GitTrust;
import dev.jagt.orchestrator.task.NewRepo;
import dev.jagt.orchestrator.task.NewTask;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/** Gives a task that planned in a scratch folder the worktrees its approved plan names, and the scratch folder goes. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanPlacement {

    private final StateService stateService;
    private final TaskArtifacts artifacts;
    private final ConfigService configService;
    private final NewTaskWorktrees worktrees;
    private final RetiredWorktrees retired;

    /** Whether the task was placed; false where it already holds a repository. */
    public boolean place(String taskId) {
        TaskState task = stateService.task(taskId).filter(TaskState::unplaced).orElse(null);
        if (task == null) {
            return false;
        }
        List<String> keys = PlanRepositories.changedIn(artifacts.read(taskId, Artifact.PLAN.fileName()));
        if (keys.isEmpty()) {
            throw Refusal.byState("plan.md names no `- [changed] <key>` under `## Repositories`, so no repository can be cut.");
        }
        ConfigService.ConfigFile config = configService.load();
        NewTask request = NewTask.builder(taskId, keys.getFirst()).alsoIn(keys.subList(1, keys.size()))
                .baseBranch(task.baseBranch()).title(task.title()).ticketUrl(task.ticketUrl()).build();
        Path scratch = Path.of(task.worktreePath());
        worktrees.place(request, config, repos -> register(taskId, task, scratch, repos));
        retired.remove(taskId, task.repos(), config.projects());
        log.atInfo().setMessage("task placed").addKeyValue("task", taskId).addKeyValue("projects", keys).log();
        return true;
    }

    private void register(String taskId, TaskState task, Path scratch, List<NewRepo> repos) {
        WorktreeFiles.copyFiles(scratch, repos.getFirst().worktreePath(), Artifact.fileNames());
        String override = task.baseBranch() == null || task.baseBranch().isBlank() ? null : repos.getFirst().baseBranch();
        List<TaskRepo> placed = repos.stream().map(NewRepo::registered).toList();
        GitTrust trust = worktrees.trust(repos);
        stateService.updateTask(taskId, current -> {
            TaskState moved = TaskReducer.placed(current, placed, override);
            return trust == null ? moved : TaskReducer.gitTrusted(moved, trust);
        });
    }
}
