package dev.jagt.orchestrator.job.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.review.ReviewDrafts;
import dev.jagt.orchestrator.service.stop.TaskStops;
import dev.jagt.orchestrator.service.turn.TaskTurns;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.turn.Turn;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ShipDueTest {

  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final ReviewDrafts drafts = mock(ReviewDrafts.class);
  private final TaskStops stops = mock(TaskStops.class);

  @Test
  void isNotDueForHandBackWhoseBranchIsPushedUnchangedAndDraftsNothing() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }

  @Test
  void isDueForHandBackCarryingNewWork() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(changes.anythingNewToShip(task)).thenReturn(true);
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isTrue();
  }

  @Test
  void isDueForHandBackWhoseBranchIsUnchangedButDraftsReplies() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(drafts.drafted(task)).thenReturn(true);
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isTrue();
  }

  @Test
  void isNotDueWhereTheLoopStopForbidsAnotherShip() {
    TaskState task = TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(changes.anythingNewToShip(task)).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }

  @Test
  void shipPressedOnThisVeryTreeIsNotDueAgain() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(Turn.none().withMark(TaskTurns.PRESSED_SHIP, "tree-1;"));
    when(changes.anythingNewToShip(task)).thenReturn(true);
    when(changes.state(task)).thenReturn(Optional.of("tree-1;"));
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }

  @Test
  void treeChangedSinceTheLastPressIsDue() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(Turn.none().withMark(TaskTurns.PRESSED_SHIP, "tree-1;"));
    when(changes.anythingNewToShip(task)).thenReturn(true);
    when(changes.state(task)).thenReturn(Optional.of("tree-2;"));
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isTrue();
  }

  @Test
  void roundAnsweredByNewDraftsOnTheSameTreeIsDueAgain() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(Turn.none().withMark(TaskTurns.PRESSED_SHIP, "tree-1;"));
    when(drafts.drafted(task)).thenReturn(true);
    when(drafts.unspentDigest(task)).thenReturn(Optional.of("d2"));
    when(changes.state(task)).thenReturn(Optional.of("tree-1;"));
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isTrue();
  }

  @Test
  void theSameTreeAndTheSameDraftsAreNotDueAgain() {
    TaskState task =
        TaskState.builder("demo", "/wt", TaskStatus.REVIEW_PENDING)
            .build()
            .withTurn(Turn.none().withMark(TaskTurns.PRESSED_SHIP, "tree-1;|drafts:d1"));
    when(drafts.drafted(task)).thenReturn(true);
    when(drafts.unspentDigest(task)).thenReturn(Optional.of("d1"));
    when(changes.state(task)).thenReturn(Optional.of("tree-1;"));
    when(stops.mayShip("ABC-42")).thenReturn(true);

    assertThat(
            new ShipDue(changes, drafts, stops, new ShipContent(changes, drafts))
                .due("ABC-42", task))
        .isFalse();
  }
}
