package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.task.FailedJob;
import dev.jagt.orchestrator.task.InfraRetry;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TaskStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Whose fault a red read is, asked of the host and never of a model: infrastructure is waited out by arming a
 * retry, anything else, a pipeline that cannot be read included, is the session's.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedCauses {

    /** {@code waiting} says what is armed, blank where nothing is; {@code stop} stands where the task was stopped. */
    public record Verdict(RedCause cause, boolean retryLeft, String waiting, Optional<TaskStop> stop) {

        static final Verdict CODE = new Verdict(RedCause.CODE, false, "", Optional.empty());
    }

    private final PipelineRetry pipelines;
    private final InfraRetries retries;
    private final InfraWaitProperties properties;

    public Verdict judge(String taskId, ReviewFacts facts, Pipeline checks) {
        if (checks != Pipeline.RED) {
            if (retries.applies(taskId)) {
                retries.clear(taskId, checks == Pipeline.GREEN);
            }
            return Verdict.CODE;
        }
        if (facts.pipelineUrl().isBlank() || !retries.applies(taskId)) {
            return Verdict.CODE;
        }
        return judgeRed(taskId, facts.pipelineUrl());
    }

    /** The same ask of the pipeline a deploy commit ran, which no round of the review request carries. */
    public Verdict judgeDeploy(String taskId, String pipelineUrl) {
        return pipelineUrl.isBlank() ? Verdict.CODE : judgeRed(taskId, pipelineUrl);
    }

    private Verdict judgeRed(String taskId, String pipelineUrl) {
        if (!properties.waits()) {
            return Verdict.CODE;
        }
        Answer<List<FailedJob>> failed = pipelines.failedJobs(pipelineUrl);
        if (failed.facts().isEmpty()) {
            log.atWarn().setMessage("failed jobs unreadable").addKeyValue("task", taskId)
                    .addKeyValue("cause", failed.failure()).log();
            return Verdict.CODE;
        }
        if (RedCause.of(failed.facts().get()) == RedCause.CODE) {
            retries.clear(taskId, true);
            return Verdict.CODE;
        }
        return waitOut(taskId, pipelineUrl, failed.facts().get());
    }

    private Verdict waitOut(String taskId, String pipelineUrl, List<FailedJob> failed) {
        String jobs = failed.stream().map(FailedJob::name).collect(Collectors.joining(", "));
        String reasons = failed.stream().map(FailedJob::reason).distinct().collect(Collectors.joining(", "));
        Optional<InfraRetry> armed = retries.arm(taskId, pipelineUrl, jobs, reasons);
        return armed.isPresent()
                ? new Verdict(RedCause.INFRASTRUCTURE, true, retries.reading(taskId), Optional.empty())
                : stopped(retries.exhausted(taskId));
    }

    private static Verdict stopped(Optional<TaskStop> stop) {
        return new Verdict(RedCause.CODE, false, "", stop);
    }
}
