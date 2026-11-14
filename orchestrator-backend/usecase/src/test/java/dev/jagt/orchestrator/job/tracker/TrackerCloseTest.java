package dev.jagt.orchestrator.job.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.TrackerWorkflow;
import dev.jagt.orchestrator.port.assistant.Answer;
import dev.jagt.orchestrator.service.assistant.TicketReader;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.launch.TicketFacts;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrackerCloseTest {

  private final TicketReader tickets = mock(TicketReader.class);
  private final TrackerWorkflow workflow = mock(TrackerWorkflow.class);
  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final TrackerClose close = new TrackerClose(tickets, workflow, changes);
  private final TaskState task =
      TaskState.builder("proj", "/wt", TaskStatus.DEPLOYED).alias("a1").build();
  private final TicketFacts item =
      TicketFacts.defaults()
          .withExists(true)
          .withKey("ABC-42")
          .withTitle("Widget")
          .withUrl("https://tracker/ABC-42")
          .withTrackerStatus("Ready for Stage");

  @BeforeEach
  void anItemInTheClosingStageTheWorkflowClosesOn() {
    when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(item), TokenUsage.NONE));
    when(workflow.closesWork(item)).thenReturn(true);
  }

  @Test
  void closesTaskWhoseItemLandedAndWhoseWorktreeHoldsNothingUncommitted() {
    when(changes.uncommitted(task)).thenReturn(Optional.of(false));

    assertThat(close.closes("ABC-42", task)).isTrue();
  }

  @Test
  void refusesToCloseOverWorkNobodyHasCommitted() {
    when(changes.uncommitted(task)).thenReturn(Optional.of(true));

    assertThat(close.closes("ABC-42", task)).isFalse();
  }

  @Test
  void refusesToCloseWhenGitCouldNotSayWhatTheWorktreeHolds() {
    when(changes.uncommitted(task)).thenReturn(Optional.empty());

    assertThat(close.closes("ABC-42", task)).isFalse();
  }

  @Test
  void refusesToCloseOnStageNobodyCouldRead() {
    when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.empty(), TokenUsage.NONE));

    assertThat(close.closes("ABC-42", task)).isFalse();
    verify(changes, never()).uncommitted(any());
  }

  @Test
  void chargesTheTokensTheTrackerReadCost() {
    TokenUsage spent = new TokenUsage(2, 120, 0, 30, 0);
    when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.empty(), spent));

    close.closes("ABC-42", task);

    verify(tickets).charge("ABC-42", spent);
  }

  @Test
  void leavesTaskOpenWhoseItemHasNotReachedTheClosingStage() {
    TicketFacts waiting = item.withTrackerStatus("In Progress");
    when(tickets.read("ABC-42")).thenReturn(new Answer<>(Optional.of(waiting), TokenUsage.NONE));
    when(workflow.closesWork(waiting)).thenReturn(false);

    assertThat(close.closes("ABC-42", task)).isFalse();
    verify(changes, never()).uncommitted(any());
  }

  @Test
  void watchesTaskNamedAfterAnItemTheWorkflowKnowsAndNoOther() {
    when(workflow.isItemKey("ABC-42")).thenReturn(true);

    assertThat(close.watches("ABC-42")).isTrue();
    assertThat(close.watches("feature/widget")).isFalse();
  }
}
