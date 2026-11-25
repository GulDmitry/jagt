package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.plan.Artifact;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The pre-ship read's findings stay open until the session writes its blocks, and the change is
 * read before it ships, with or without a request. A read that failed, or a reviewer that is off,
 * holds nothing.
 */
@Service
@RequiredArgsConstructor
public class UnansweredFindings {

  private final StateService stateService;
  private final HandBackDue handBackDue;
  private final CodeReviewSettings settings;
  private final WorktreeChanges changes;

  /** Empty where none awaits an answer or a read; else the sentence a ship is declined with. */
  public Optional<String> awaiting(String taskId) {
    return stateService.task(taskId).flatMap(task -> answers(task).or(() -> unread(task)));
  }

  private Optional<String> answers(TaskState task) {
    return handBackDue
        .unanswered(task)
        .map(round ->
            round.threads()
                + " finding(s) of the latest review round await your block in "
                + Artifact.REPLIES.fileName()
                + " — answer each, then ship");
  }

  private Optional<String> unread(TaskState task) {
    if (!PreShipRead.due(task) || !settings.load().enabled()) {
      return Optional.empty();
    }
    return readOrFailed(task, changes.reviewKey(task))
        ? Optional.empty()
        : Optional.of(
            "the review before the ship has not read this change yet — it reads within a minute,"
                + " then ship");
  }

  private static boolean readOrFailed(TaskState task, String tree) {
    ToldOnce told = task.round().toldOrNone();
    return told.said(ToldOnce.Kind.PRE_SHIP_TREE, tree)
        || told.said(ToldOnce.Kind.PRE_SHIP_FAILED, tree);
  }
}
