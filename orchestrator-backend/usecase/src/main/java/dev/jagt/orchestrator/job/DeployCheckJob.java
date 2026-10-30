package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.StopKind;
import dev.jagt.orchestrator.flow.Watch;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.StepRelay;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.ChecksUnread;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.Step;
import dev.jagt.orchestrator.task.Turn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asks every deployed task's session to check the change where it landed, once per deploy, as the brief's deploy
 * section says, and only once the pipeline jagt reads on each deploy commit is green; until then the card says what
 * the deploy waits on. A check that fails is the session's IN_PROGRESS, and the round goes round again: reviewed,
 * deployed, checked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployCheckJob implements TaskJob {

    private final ConfigService configService;
    private final StepRelay relay;
    private final DeployPipelines pipelines;
    private final FlowReports reports;
    private final TaskStops stops;
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
        return Watch.defaults().withStatuses(FlowRules::checkedWhereItLanded)
                .withStops(StopKind.NONE, StopKind.DEPLOY_READ);
    }

    @Override
    public void run(String taskId, TaskState task) {
        if (task.stopped()) {
            readStopped(taskId, task);
            return;
        }
        landed(taskId, task).ifPresent(landed -> {
            if (!asked(task, Turn.digestOf(landed))) {
                settle(taskId, task, landed);
            } else if (!bareOf(task).isEmpty()) {
                readLate(taskId, task);
            }
        });
    }

    /** The read that ends a stop set because the deploy's pipelines could not be read, run while it stands. */
    private void readStopped(String taskId, TaskState task) {
        DeployPipelines.Reading reading = pipelines.read(taskId, task);
        if (counted(taskId, task, reading) == 0) {
            stops.endOnDeployRead(taskId).ifPresent(stop -> log.atInfo().setMessage("stop voided by read")
                    .addKeyValue("task", taskId).addKeyValue("trigger", stop.trigger()).log());
        }
    }

    /** The reads running that could not list a pipeline; at the ceiling the task stops for the human. */
    private int counted(String taskId, TaskState task, DeployPipelines.Reading reading) {
        boolean unread = reading.kind() == DeployPipelines.Kind.UNREAD;
        if (!unread && unreadOf(task) == 0) {
            return 0;
        }
        int running = reports.deployReadOn(taskId, unread ? reading.project() : null);
        if (running >= ChecksUnread.CEILING && !task.stopped()) {
            stops.stop(taskId, TaskStop.Trigger.DEPLOY_UNREAD,
                    running + " reads running could not list the deploy pipeline: " + reading.said());
        }
        return running;
    }

    private static int unreadOf(TaskState task) {
        return task.deployment() == null ? 0 : task.deployment().targets().stream()
                .mapToInt(Deployment.Target::unreadReads).max().orElse(0);
    }

    /** A pipeline the host lists after the deploy settled bare is read still: red hands it back, green is the end. */
    private void readLate(String taskId, TaskState task) {
        DeployPipelines.Reading reading = pipelines.read(taskId, task);
        counted(taskId, task, reading);
        switch (reading.kind()) {
            case RED -> reports.readDeploy(taskId, reading.project(), reading.said() + " " + reading.url());
            case GREEN -> markBare(taskId, task, reading.bare());
            case WAITING, UNREAD -> {
            }
        }
    }

    private void markBare(String taskId, TaskState task, List<String> bare) {
        if (!bareOf(task).equals(Set.copyOf(bare))) {
            reports.deployBare(taskId, bare);
        }
    }

    private static Set<String> bareOf(TaskState task) {
        return task.deployment() == null ? Set.of() : task.deployment().targets().stream()
                .filter(Deployment.Target::isBare).map(Deployment.Target::project).collect(Collectors.toSet());
    }

    private void settle(String taskId, TaskState task, String landed) {
        DeployPipelines.Reading reading = pipelines.read(taskId, task);
        counted(taskId, task, reading);
        String stamp = "deployed to " + deployBranches(task);
        switch (reading.kind()) {
            case GREEN -> {
                markBare(taskId, task, reading.bare());
                reports.readDeploy(taskId, null, stamp);
                if (relay.relay(taskId, STEP, landed, relay.standing(taskId))) {
                    reports.deployAsked(taskId, Turn.digestOf(landed));
                }
            }
            case WAITING, UNREAD -> reports.readDeploy(taskId, null, stamp + " — waiting on its pipeline: " + reading.said());
            case RED -> reports.readDeploy(taskId, reading.project(), reading.said() + " " + reading.url());
        }
    }

    private static boolean asked(TaskState task, String digest) {
        return task.deployment() != null && digest.equals(task.deployment().askedDigest());
    }

    private String deployBranches(TaskState task) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        return task.repos().stream().map(repo -> projects.get(repo.project()))
                .filter(project -> project != null && project.deployBranch() != null).map(ProjectConfig::deployBranch)
                .distinct().collect(Collectors.joining(", "));
    }

    private Optional<String> landed(String taskId, TaskState task) {
        Map<String, ProjectConfig> projects = configService.load().projects();
        String landed = (task.deployment() == null ? List.<Deployment.Target>of() : task.deployment().targets())
                .stream().filter(Deployment.Target::landed)
                .map(target -> landed(target, projects.get(target.project())))
                .collect(Collectors.joining("\n"));
        return landed.isEmpty() ? Optional.empty() : Optional.of(taskId + " is deployed:\n" + landed);
    }

    private static String landed(Deployment.Target target, @Nullable ProjectConfig project) {
        String commit = Objects.requireNonNull(target.tip());
        String sha = commit.length() > 8 ? commit.substring(0, 8) : commit;
        String branch = project == null || project.deployBranch() == null ? "its deploy branch"
                : project.deployBranch();
        return "- " + target.project() + ": " + sha + " on " + branch;
    }
}
