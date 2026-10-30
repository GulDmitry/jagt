package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.task.DeployRedLine;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Whether a deploy stands: the host's pipeline on each commit it pushed, read by jagt. Anything but green keeps the
 * deploy open; red the code owns is the one verdict that hands it back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeployPipelines {

    public enum Kind { GREEN, WAITING, UNREAD, RED }

    /**
     * {@code said} names the repository, the commit and what the pipeline did; {@code url} is the pipeline's, blank
     * where none ran; {@code bare}, the repositories green only because the host listed no pipeline in time.
     */
    public record Reading(Kind kind, String said, String project, String url, List<String> bare) {

        static Reading green() {
            return new Reading(Kind.GREEN, "", "", "", List.of());
        }

        static Reading bare(String project) {
            return new Reading(Kind.GREEN, "", "", "", List.of(project));
        }

        static Reading waiting(String said) {
            return new Reading(Kind.WAITING, said, "", "", List.of());
        }
    }

    /** How long after the push a host listing no pipeline for a commit still counts as one about to come. */
    public static final Duration NO_PIPELINE_AFTER = Duration.ofMinutes(10);

    private final PipelineRetry pipelines;
    private final RedCauses causes;
    private final DeployFixWorktrees fixes;
    private final Clock clock;

    /** The first red the code owns, else the first repository still open, else green. */
    public Reading read(String taskId, TaskState task) {
        @Nullable Reading open = null;
        List<String> bare = new ArrayList<>();
        for (Deployment.Target landed : landedTargets(task)) {
            Reading one = read(taskId, task, landed);
            if (one.kind() == Kind.RED) {
                return one;
            }
            open = stillOpen(open, one);
            bare.addAll(one.bare());
        }
        if (open != null) {
            return open;
        }
        causes.deployStands(taskId);
        return new Reading(Kind.GREEN, "", "", "", List.copyOf(bare));
    }

    /** The first repository still open, a read that could not list its pipeline before one merely waiting. */
    private static @Nullable Reading stillOpen(@Nullable Reading open, Reading one) {
        if (one.kind() == Kind.GREEN) {
            return open;
        }
        return open == null || open.kind() == Kind.WAITING && one.kind() == Kind.UNREAD ? one : open;
    }

    private static List<Deployment.Target> landedTargets(TaskState task) {
        return task.deployment() == null ? List.of()
                : task.deployment().targets().stream().filter(Deployment.Target::landed).toList();
    }

    private Reading read(String taskId, TaskState task, Deployment.Target landed) {
        String commit = Objects.requireNonNull(landed.tip());
        String project = landed.project();
        String remoteUrl = task.repo(project).map(TaskRepo::remoteUrl).orElse(null);
        String label = project + " " + GitDeploy.shortSha(commit);
        String at = label + ": pipeline ";
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return withoutPipeline(taskId, label, "no remote recorded");
        }
        Answer<CommitPipeline> read = pipelines.onCommit(remoteUrl, commit);
        if (read.facts().isEmpty()) {
            log.atWarn().setMessage("deploy pipeline unreadable").addKeyValue("project", project)
                    .addKeyValue("commit", GitDeploy.shortSha(commit)).addKeyValue("cause", read.failure())
                    .addKeyValue("effect", "the deploy stays open").log();
            return new Reading(Kind.UNREAD, at + "unread, " + read.failure(), project, "", List.of());
        }
        CommitPipeline pipeline = read.facts().get();
        if (!pipeline.hosted()) {
            return withoutPipeline(taskId, label, "no code-host address");
        }
        return switch (Pipeline.of(pipeline.status())) {
            case GREEN -> Reading.green();
            case RED -> red(taskId, task, label, project, at, pipeline.url());
            case RUNNING -> Reading.waiting(at + "running");
            case NONE -> none(taskId, landed, label, at);
            case UNKNOWN -> Reading.waiting(at + "state unknown");
        };
    }

    private Reading none(String taskId, Deployment.Target landed, String label, String at) {
        if (landed.isBare()) {
            return Reading.bare(landed.project());
        }
        if (clock.millis() - landed.pushedAt() < NO_PIPELINE_AFTER.toMillis()) {
            return Reading.waiting(at + "not started");
        }
        withoutPipeline(taskId, label, "the host listed none after " + NO_PIPELINE_AFTER.toMinutes() + " min");
        return Reading.bare(landed.project());
    }

    /** The one way a deploy settles with no pipeline behind it: none can come, so none is waited for. */
    private Reading withoutPipeline(String taskId, String label, String why) {
        log.atInfo().setMessage("deploy settled without a pipeline").addKeyValue("task", taskId)
                .addKeyValue("commit", label).addKeyValue("cause", why).log();
        return Reading.green();
    }

    private Reading red(String taskId, TaskState task, String label, String project, String at, String url) {
        RedCauses.Verdict verdict = causes.judgeDeploy(taskId, url);
        if (verdict.cause() == RedCause.INFRASTRUCTURE) {
            return Reading.waiting(at + "red on infrastructure, " + verdict.waiting());
        }
        if (verdict.stop().isPresent()) {
            return Reading.waiting(at + "red on infrastructure, " + verdict.stop().get().reading());
        }
        try {
            fixes.open(task, taskId, project);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("deploy worktree not opened").addKeyValue("task", taskId)
                    .addKeyValue("project", project).addKeyValue("cause", e.getMessage())
                    .addKeyValue("effect", "the deploy stays open, tried again").log();
            return Reading.waiting(at + "red on the code, its deploy worktree not opened: " + e.getMessage());
        }
        return new Reading(Kind.RED, DeployRedLine.CAUSE + ": " + label, project, url, List.of());
    }
}
