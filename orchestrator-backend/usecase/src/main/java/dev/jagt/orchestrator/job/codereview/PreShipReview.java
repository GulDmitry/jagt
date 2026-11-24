package dev.jagt.orchestrator.job.codereview;

import dev.jagt.orchestrator.job.codereview.request.Subject;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.codehost.review.RequestChange;
import dev.jagt.orchestrator.service.PreShipRead;
import dev.jagt.orchestrator.service.RoundRelay;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * A task's diff against its base, read once per tree state while the session has handed back and no
 * request exists, each finding relayed to the session as a review round. A clean read relays
 * nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreShipReview {

  private static final String AUTHOR = "code-reviewer: ";

  private final StateService stateService;
  private final WorktreeChanges changes;
  private final CodeReviewPanel panel;
  private final PreShipCoverage coverage;
  private final RoundRelay relay;

  public void run(String model) {
    stateService.tasks().forEach((taskId, task) -> run(taskId, task, model));
  }

  private void run(String taskId, TaskState task, String model) {
    if (!PreShipRead.due(task)) {
      return;
    }
    Optional<String> tree = changes.state(task);
    if (tree.isEmpty() || task.round().toldOrNone().said(ToldOnce.Kind.PRE_SHIP_TREE, tree.get())) {
      return;
    }
    String diff = changes.diff(task);
    if (diff.isBlank()) {
      told(taskId, ToldOnce.Kind.PRE_SHIP_TREE, tree.get());
    } else {
      read(taskId, task, tree.get(), diff, model);
    }
  }

  private void read(String taskId, TaskState task, String tree, String diff, String model) {
    Subject subject =
        new Subject(
            Objects.requireNonNull(task.project()),
            "the local change of " + taskId + ", not yet shipped",
            String.valueOf(task.remoteUrl()),
            taskId);
    RequestChange change =
        RequestChange.defaults()
            .withTitle(Objects.toString(task.ticket().title(), ""))
            .withBaseBranch(Objects.toString(task.ticket().baseBranch(), "its base branch"))
            .withHeadSha(tree)
            .withDiff(diff);
    Judgement judged =
        panel.review(subject, change, Path.of(Objects.requireNonNull(task.worktreePath())), model);
    if (!judged.failure().isBlank()) {
      log.atWarn()
          .setMessage("pre-ship review failed")
          .addKeyValue("task", taskId)
          .addKeyValue("cause", judged.failure())
          .addKeyValue("effect", "the tree is read again on the next tick")
          .log();
      told(taskId, ToldOnce.Kind.PRE_SHIP_FAILED, tree);
      return;
    }
    List<Finding> findings =
        judged.findings().stream().filter(finding -> !"noise".equals(finding.severity())).toList();
    log.atInfo()
        .setMessage("pre-ship review read")
        .addKeyValue("task", taskId)
        .addKeyValue("key", tree)
        .addKeyValue("value", findings.size())
        .log();
    if (findings.isEmpty() || relayed(taskId, tree, findings)) {
      if (!findings.isEmpty()) {
        log.atInfo()
            .setMessage("pre-ship review relayed")
            .addKeyValue("task", taskId)
            .addKeyValue("value", findings.size())
            .log();
        coverage.raise(taskId, findings);
      }
      told(taskId, ToldOnce.Kind.PRE_SHIP_TREE, tree);
    }
  }

  private void told(String taskId, ToldOnce.Kind kind, String tree) {
    stateService.reduce(taskId, (reducer, current) -> reducer.told(current, kind, tree));
  }

  private boolean relayed(String taskId, String tree, List<Finding> findings) {
    ReviewFacts round =
        new ReviewFacts(
            new ReviewFacts.Request(true, false, false, false, 0),
            new ReviewFacts.Pipeline("", "", ""),
            new ReviewFacts.Discussion(
                findings.stream().map(finding -> AUTHOR + CodeReviewRound.text(finding)).toList(),
                List.of(),
                findings.stream().map(PreShipCoverage::key).toList()));
    SweepResult.Kind kind =
        relay.relay(taskId, "the task's change before its ship", round, "none", tree).kind();
    return kind == SweepResult.Kind.RELAYED || kind == SweepResult.Kind.UNCHANGED;
  }
}
