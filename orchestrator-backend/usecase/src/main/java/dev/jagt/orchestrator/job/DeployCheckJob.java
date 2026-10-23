package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Asks every deployed task's session to check the change where it landed, once per deploy, as the brief's deploy
 * section says. A check that fails is the session's IN_PROGRESS, and the round goes round again: reviewed, deployed,
 * checked.
 */
@Service
@RequiredArgsConstructor
public class DeployCheckJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final StepRelay relay;
    private static final String STEP = "deploy";
    /** Keyed off the commits that landed, not the context file, which every other relay overwrites. */
    private final Map<String, String> asked = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "deploy-check";
    }

    @Override
    public Serves serves() {
        return Serves.TASKS;
    }

    @Override
    public String describe() {
        return "ask each deployed task's session to check the change where it landed";
    }

    @Override
    public Duration every() {
        return Duration.ofSeconds(20);
    }

    @Override
    public void run() {
        stateService.tasks().forEach((taskId, task) -> {
            if (!FlowRules.checkedWhereItLanded(task.status())) {
                return;
            }
            landed(taskId, task).filter(landed -> !landed.equals(asked.get(taskId))).ifPresent(landed -> {
                relay.relay(taskId, STEP, landed);
                asked.put(taskId, landed);
            });
        });
    }

    private Optional<String> landed(String taskId, TaskState task) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        String landed = task.repos().stream()
                .filter(repo -> repo.deployCommit() != null && !repo.deployCommit().isBlank())
                .map(repo -> landed(repo, projects.get(repo.project())))
                .collect(Collectors.joining("\n"));
        return landed.isEmpty() ? Optional.empty() : Optional.of(taskId + " is deployed:\n" + landed);
    }

    private static String landed(TaskRepo repo, @Nullable ProjectConfig project) {
        String commit = Objects.requireNonNull(repo.deployCommit());
        String sha = commit.length() > 8 ? commit.substring(0, 8) : commit;
        String branch = project == null || project.deployBranch() == null ? "its deploy branch"
                : project.deployBranch();
        return "- " + repo.project() + ": " + sha + " on " + branch;
    }
}
