package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.InfraWaitProperties;
import dev.jagt.orchestrator.flow.deploy.Pipeline;
import dev.jagt.orchestrator.flow.deploy.RedCause;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.port.codehost.CodeHost;
import dev.jagt.orchestrator.service.deploy.InfraRetries;
import dev.jagt.orchestrator.task.deploy.FailedJob;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Whose fault a red read is, asked of the host and never of a model: infrastructure is waited out
 * by arming a retry, anything else, a pipeline that cannot be read included, is the session's.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedCauses {

  /**
   * {@code waiting} says what is armed, blank where nothing is; {@code stop} stands where the task
   * was stopped; {@code failed} names each failed job with its reason, blank where none was read.
   */
  public record Verdict(
      RedCause cause, boolean retryLeft, String waiting, Optional<TaskStop> stop, String failed) {

    static final Verdict CODE = new Verdict(RedCause.CODE, false, "", Optional.empty());

    public Verdict(RedCause cause, boolean retryLeft, String waiting, Optional<TaskStop> stop) {
      this(cause, retryLeft, waiting, stop, "");
    }
  }

  private final CodeHost codeHost;
  private final InfraRetries retries;
  private final InfraWaitProperties properties;

  public Verdict judge(String taskId, String project, ReviewFacts facts, Pipeline checks) {
    if (checks != Pipeline.RED) {
      if (retries.applies(taskId)) {
        if (checks == Pipeline.GREEN) {
          retries.clearAll(taskId);
        } else {
          retries.clearArmed(taskId);
        }
      }
      return Verdict.CODE;
    }
    if (facts.pipeline().url().isBlank() || !retries.applies(taskId)) {
      return Verdict.CODE;
    }
    return judgeRed(taskId, project, facts.pipeline().url());
  }

  /**
   * The same ask of the pipeline a deploy commit ran, which no round of the review request carries.
   */
  public Verdict judgeDeploy(String taskId, String project, String pipelineUrl) {
    return pipelineUrl.isBlank() ? Verdict.CODE : judgeRed(taskId, project, pipelineUrl);
  }

  /** The deploy's pipeline went green: no retry is owed for it. */
  public void deployStands(String taskId) {
    retries.clearAll(taskId);
  }

  private Verdict judgeRed(String taskId, String project, String pipelineUrl) {
    if (!properties.waits()) {
      return Verdict.CODE;
    }
    Answer<List<FailedJob>> failed = codeHost.failedJobs(project, pipelineUrl);
    if (failed.facts().isEmpty()) {
      log.atWarn()
          .setMessage("failed jobs unreadable")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", failed.failure())
          .log();
      return Verdict.CODE;
    }
    if (RedCause.of(failed.facts().get()) == RedCause.CODE) {
      retries.clearAll(taskId);
      return new Verdict(
          RedCause.CODE,
          false,
          "",
          Optional.empty(),
          failed.facts().get().stream()
              .map(job ->
                  job.reason().isEmpty() ? job.name() : job.name() + " (" + job.reason() + ")")
              .collect(Collectors.joining(", ")));
    }
    return waitOut(taskId, project, pipelineUrl, failed.facts().get());
  }

  private Verdict waitOut(
      String taskId, String project, String pipelineUrl, List<FailedJob> failed) {
    String jobs = failed.stream().map(FailedJob::name).collect(Collectors.joining(", "));
    String reasons =
        failed.stream().map(FailedJob::reason).distinct().collect(Collectors.joining(", "));
    Optional<InfraRetry> armed = retries.arm(taskId, project, pipelineUrl, jobs, reasons);
    return armed.isPresent()
        ? new Verdict(RedCause.INFRASTRUCTURE, true, retries.reading(taskId), Optional.empty())
        : stopped(retries.exhausted(taskId));
  }

  private static Verdict stopped(Optional<TaskStop> stop) {
    return new Verdict(RedCause.CODE, false, "", stop);
  }
}
