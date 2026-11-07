package dev.jagt.orchestrator.job;

import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.Pipeline;
import dev.jagt.orchestrator.flow.RedCause;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.CommitPipeline;
import dev.jagt.orchestrator.port.PipelineRetry;
import dev.jagt.orchestrator.service.Causes;
import dev.jagt.orchestrator.service.GitDeploy;
import dev.jagt.orchestrator.service.RedCauses;
import dev.jagt.orchestrator.task.DeployRedLine;
import dev.jagt.orchestrator.task.Deployment;
import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Whether a deploy stands: the host's pipeline on each commit it pushed, read by jagt. Anything but
 * green keeps the deploy open; red the code owns is the one verdict that hands it back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeployPipelines {

  /**
   * Declared in the order a read still open outranks another: a read that reached the host and
   * failed outranks one that never reached it.
   */
  public enum Kind {
    GREEN,
    WAITING,
    UNREACHED,
    UNREAD,
    RED
  }

  /**
   * {@code said} names the repository, the commit and what the pipeline did; {@code url} is the
   * pipeline's, blank where none ran; {@code bare}, the repositories green only because the host
   * listed no pipeline in time.
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

  /**
   * How long after the push a host listing no pipeline for a commit still counts as one about to
   * come.
   */
  public static final Duration NO_PIPELINE_AFTER = Duration.ofMinutes(10);

  private final PipelineRetry pipelines;
  private final RedCauses causes;
  private final DeployFixWorktrees fixes;
  private final Clock clock;
  private final FlowReports reports;

  /**
   * Whether a pipeline on each held repository's deploy branch, newer than the red one and
   * containing its commit, is green, or the task no longer holds it. Anything else, unread
   * included, leaves the conflict standing; nothing is re-run or opened.
   */
  public boolean redConflictDisproved(TaskState task) {
    Deployment deployment = task.deployment();
    return deployment != null
        && deployment.heldByRedPipeline()
        && deployment.targets().stream()
            .filter(Deployment.Target::conflicted)
            .allMatch(target -> greenSince(task, target));
  }

  private boolean greenSince(TaskState task, Deployment.Target held) {
    Optional<TaskRepo> repo = task.repo(held.project());
    if (repo.isEmpty()) {
      return true;
    }
    String remoteUrl = repo.get().remoteUrl();
    if (!held.landed() || remoteUrl == null || remoteUrl.isBlank()) {
      return false;
    }
    return pipelines
        .onBranchSince(
            held.project(), remoteUrl, held.deployBranch(), Objects.requireNonNull(held.tip()))
        .facts()
        .filter(read -> read.hosted() && Pipeline.of(read.status()) == Pipeline.GREEN)
        .isPresent();
  }

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

  private Reading read(String taskId, TaskState task, Deployment.Target landed) {
    String commit = Objects.requireNonNull(landed.tip());
    String project = landed.project();
    String remoteUrl = task.repo(project).map(TaskRepo::remoteUrl).orElse(null);
    String label = project + " " + GitDeploy.shortSha(commit);
    String at = label + ": pipeline ";
    if (remoteUrl == null || remoteUrl.isBlank()) {
      return withoutPipeline(taskId, label, "no remote recorded");
    }
    Answer<CommitPipeline> read = pipelines.onCommit(project, remoteUrl, commit);
    if (read.facts().isEmpty() && read.reach().isPresent()) {
      return new Reading(
          Kind.UNREACHED, at + "unreached, " + read.failure(), project, "", List.of());
    }
    if (read.facts().isEmpty()) {
      log.atWarn()
          .setMessage("deploy pipeline unreadable")
          .addKeyValue("project", project)
          .addKeyValue("commit", GitDeploy.shortSha(commit))
          .addKeyValue("cause", read.failure())
          .addKeyValue("effect", "the deploy stays open")
          .log();
      return new Reading(Kind.UNREAD, at + "unread, " + read.failure(), project, "", List.of());
    }
    CommitPipeline pipeline = read.facts().get();
    if (!pipeline.hosted()) {
      return withoutPipeline(taskId, label, "no code-host address");
    }
    return switch (Pipeline.of(pipeline.status())) {
      case GREEN -> Reading.green();
      case RED -> red(taskId, task, landed, label, at, pipeline.url());
      case RUNNING -> Reading.waiting(at + "running");
      case NONE -> none(taskId, landed, label, at);
      case UNKNOWN -> Reading.waiting(at + "state unknown");
    };
  }

  /** The first repository still open, by {@link Kind} order. */
  private static @Nullable Reading stillOpen(@Nullable Reading open, Reading one) {
    if (one.kind() == Kind.GREEN) {
      return open;
    }
    return open == null || one.kind().compareTo(open.kind()) > 0 ? one : open;
  }

  private static List<Deployment.Target> landedTargets(TaskState task) {
    return task.deployment() == null
        ? List.of()
        : task.deployment().targets().stream().filter(Deployment.Target::landed).toList();
  }

  private Reading none(String taskId, Deployment.Target landed, String label, String at) {
    if (landed.isBare()) {
      return Reading.bare(landed.project());
    }
    if (clock.millis() - landed.landing().pushedAt() < NO_PIPELINE_AFTER.toMillis()) {
      return Reading.waiting(at + "not started");
    }
    withoutPipeline(
        taskId, label, "the host listed none after " + NO_PIPELINE_AFTER.toMinutes() + " min");
    return Reading.bare(landed.project());
  }

  /**
   * The one way a deploy settles with no pipeline behind it: none can come, so none is waited for.
   */
  private Reading withoutPipeline(String taskId, String label, String why) {
    log.atInfo()
        .setMessage("deploy settled without a pipeline")
        .addKeyValue("task", taskId)
        .addKeyValue("commit", label)
        .addKeyValue("cause", why)
        .log();
    return Reading.green();
  }

  private Reading red(
      String taskId,
      TaskState task,
      Deployment.Target landed,
      String label,
      String at,
      String url) {
    String project = landed.project();
    RedCauses.Verdict verdict = causes.judgeDeploy(taskId, project, url);
    if (verdict.cause() == RedCause.INFRASTRUCTURE) {
      return Reading.waiting(at + "red on infrastructure, " + verdict.waiting());
    }
    if (verdict.stop().isPresent()) {
      return Reading.waiting(at + "red on infrastructure, " + verdict.stop().get().reading());
    }
    if (!landed.retriedTip()
        && retried(taskId, project, url, Objects.requireNonNull(landed.tip()))) {
      return Reading.waiting(at + "red, re-run once before it is handed back");
    }
    if (greenSince(task, landed)) {
      return Reading.green();
    }
    try {
      fixes.open(task, taskId, project);
    } catch (RuntimeException e) {
      log.atWarn()
          .setMessage("deploy worktree not opened")
          .addKeyValue("task", taskId)
          .addKeyValue("project", project)
          .addKeyValue("cause", Causes.of(e))
          .addKeyValue("effect", "the deploy stays open, tried again")
          .log();
      return Reading.waiting(
          at + "red on the code, its deploy worktree not opened: " + e.getMessage());
    }
    return new Reading(Kind.RED, DeployRedLine.CAUSE + ": " + label, project, url, List.of());
  }

  /**
   * A red the code may own is re-run once per push: a failing job of another change, or a flaky
   * one, passes the second time.
   */
  private boolean retried(String taskId, String project, String url, String tip) {
    reports.deployRetried(taskId, project, tip);
    Answer<String> sent = pipelines.retryFailed(project, url);
    if (sent.facts().isPresent()) {
      log.atInfo()
          .setMessage("deploy pipeline retried")
          .addKeyValue("task", taskId)
          .addKeyValue("project", project)
          .addKeyValue("url", url)
          .log();
      return true;
    }
    log.atWarn()
        .setMessage("deploy pipeline not retried")
        .addKeyValue("task", taskId)
        .addKeyValue("project", project)
        .addKeyValue("cause", sent.failure())
        .addKeyValue("effect", "the red is handed back as it stands")
        .log();
    return false;
  }
}
