package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.InfraRetries;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Whether a deploy stands: the host's pipeline on each commit it pushed, read by jagt. Anything but green keeps the
 * deploy open; red the code owns is the one verdict that hands it back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeployPipelines {

    public enum Kind { GREEN, WAITING, RED }

    /** {@code said} names the repository, the commit and what the pipeline did; {@code url} is the pipeline's, blank where none ran. */
    public record Reading(Kind kind, String said, String project, String url) {

        static Reading green() {
            return new Reading(Kind.GREEN, "", "", "");
        }

        static Reading waiting(String said) {
            return new Reading(Kind.WAITING, said, "", "");
        }
    }

    private final PipelineRetry pipelines;
    private final RedCauses causes;
    private final InfraRetries retries;

    /** The first red the code owns, else the first repository still open, else green. */
    public Reading read(String taskId, TaskState task) {
        Reading open = null;
        for (TaskRepo repo : task.repos()) {
            if (repo.deployCommit() == null || repo.deployCommit().isBlank()) {
                continue;
            }
            Reading one = read(taskId, repo);
            if (one.kind() == Kind.RED) {
                return one;
            }
            open = open == null && one.kind() == Kind.WAITING ? one : open;
        }
        if (open != null) {
            return open;
        }
        retries.clear(taskId, true);
        return Reading.green();
    }

    private Reading read(String taskId, TaskRepo repo) {
        String commit = Objects.requireNonNull(repo.deployCommit());
        String project = Objects.requireNonNull(repo.project());
        String at = project + " " + GitDeploy.shortSha(commit) + ": pipeline ";
        if (repo.remoteUrl() == null || repo.remoteUrl().isBlank()) {
            return Reading.waiting(at + "unread, no remote recorded");
        }
        Answer<CommitPipeline> read = pipelines.onCommit(repo.remoteUrl(), commit);
        if (read.facts().isEmpty()) {
            log.atWarn().setMessage("deploy pipeline unreadable").addKeyValue("project", project)
                    .addKeyValue("commit", GitDeploy.shortSha(commit)).addKeyValue("cause", read.failure())
                    .addKeyValue("effect", "the deploy stays open").log();
            return Reading.waiting(at + "unread, " + read.failure());
        }
        CommitPipeline pipeline = read.facts().get();
        return switch (Pipeline.of(pipeline.status())) {
            case GREEN -> Reading.green();
            case RED -> red(taskId, project, at, pipeline.url());
            case RUNNING -> Reading.waiting(at + "running");
            case NONE -> Reading.waiting(at + "not started");
            case UNKNOWN -> Reading.waiting(at + "state unknown");
        };
    }

    private Reading red(String taskId, String project, String at, String url) {
        RedCauses.Verdict verdict = causes.judgeDeploy(taskId, url);
        if (verdict.cause() == RedCause.INFRASTRUCTURE) {
            return Reading.waiting(at + "red on infrastructure, " + verdict.waiting());
        }
        if (verdict.stop().isPresent()) {
            return Reading.waiting(at + "red on infrastructure, " + verdict.stop().get().reading());
        }
        return new Reading(Kind.RED, at + "red on the code", project, url);
    }
}
