package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.job.Job;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Asks every deployed task's session to check the change where it landed, once per deploy. A check that fails is
 * the session's IN_PROGRESS, and the round goes round again: reviewed, deployed, checked.
 */
@Service
@RequiredArgsConstructor
public class DeployCheckJob implements Job {

    private final StateService stateService;
    private final ConfigService configService;
    private final AgentSessions sessions;
    /** Keyed off what was asked, not the context file, which every other relay overwrites. */
    private final Map<String, String> asked = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "deploy-check";
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
            if (task.status() != TaskStatus.DEPLOYED) {
                return;
            }
            brief(taskId, task).filter(brief -> !brief.equals(asked.get(taskId))).ifPresent(brief -> {
                sessions.relayIfChanged(taskId, brief);
                asked.put(taskId, brief);
            });
        });
    }

    private Optional<String> brief(String taskId, TaskState task) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        String landed = task.repos().stream()
                .filter(repo -> repo.deployCommit() != null && !repo.deployCommit().isBlank())
                .map(repo -> landed(repo, projects.get(repo.project())))
                .collect(Collectors.joining("\n"));
        if (landed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(taskId + " is deployed:\n" + landed + "\n\n"
                + "Check that it works where it landed, through whatever reaches it — a CLI, HTTP, a browser — with"
                + " the skill or check this machine has for it. If it works, report nothing: end your turn with one"
                + " line naming what you checked, or what kept you from reaching it. If it does not, report"
                + " IN_PROGRESS saying what broke, fix it, and hand the round back as usual — it is reviewed,"
                + " deployed and checked again.");
    }

    private static String landed(TaskRepo repo, ProjectConfig project) {
        String sha = repo.deployCommit().length() > 8 ? repo.deployCommit().substring(0, 8) : repo.deployCommit();
        String branch = project == null || project.deployBranch() == null ? "its deploy branch"
                : project.deployBranch();
        return "- " + repo.project() + ": " + sha + " on " + branch;
    }
}
