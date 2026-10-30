package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Asks every deployed task's session to check the change where it landed, once per deploy, as the brief's deploy
 * section says, and only once the pipeline jagt reads on each deploy commit is green; until then the card says what
 * the deploy waits on. A check that fails is the session's IN_PROGRESS, and the round goes round again: reviewed,
 * deployed, checked.
 */
@Service
@RequiredArgsConstructor
public class DeployCheckJob implements TaskJob {

    private final ConfigService configService;
    private final StepRelay relay;
    private final DeployPipelines pipelines;
    private final FlowReports reports;
    private static final Step STEP = Step.DEPLOY;
    private static final Duration READ_EVERY = Duration.ofMinutes(1);

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
        return "ask each deployed task's session to check the change where it landed, once its pipeline is green";
    }

    @Override
    public Duration every() {
        return READ_EVERY;
    }

    @Override
    public Watch watch() {
        return Watch.defaults().withStatuses(FlowRules::checkedWhereItLanded).withStops(StopKind.NONE);
    }

    @Override
    public void run(String taskId, TaskState task) {
        landed(taskId, task).filter(landed -> !asked(task, Turn.digestOf(landed)))
                .ifPresent(landed -> settle(taskId, task, landed));
    }

    private void settle(String taskId, TaskState task, String landed) {
        DeployPipelines.Reading reading = pipelines.read(taskId, task);
        String stamp = "deployed to " + deployBranches(task);
        switch (reading.kind()) {
            case GREEN -> {
                reports.readDeploy(taskId, null, stamp);
                if (relay.relay(taskId, STEP, landed, relay.standing(taskId))) {
                    reports.deployAsked(taskId, Turn.digestOf(landed));
                }
            }
            case WAITING -> reports.readDeploy(taskId, null, stamp + " — waiting on its pipeline: " + reading.said());
            case RED -> reports.readDeploy(taskId, reading.project(), reading.said() + " " + reading.url());
        }
    }

    /** From the record; a deploy begun before it is read from the turn's mark, as before. */
    private static boolean asked(TaskState task, String digest) {
        return task.deployment() == null ? task.turnOrNone().marked(STEP.id(), digest)
                : digest.equals(task.deployment().askedDigest());
    }

    private String deployBranches(TaskState task) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        return task.repos().stream().map(repo -> projects.get(repo.project()))
                .filter(project -> project != null && project.deployBranch() != null).map(ProjectConfig::deployBranch)
                .distinct().collect(Collectors.joining(", "));
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
        String commit = Objects.requireNonNull(repo.deployTip());
        String sha = commit.length() > 8 ? commit.substring(0, 8) : commit;
        String branch = project == null || project.deployBranch() == null ? "its deploy branch"
                : project.deployBranch();
        return "- " + repo.project() + ": " + sha + " on " + branch;
    }
}
