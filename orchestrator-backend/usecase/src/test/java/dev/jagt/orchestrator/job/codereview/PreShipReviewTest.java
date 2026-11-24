package dev.jagt.orchestrator.job.codereview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.RoundRelay;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PreShipReviewTest {

  private final StateService state = mock(StateService.class);
  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final CodeReviewPanel panel = mock(CodeReviewPanel.class);
  private final PreShipCoverage coverage = mock(PreShipCoverage.class);
  private final RoundRelay relay = mock(RoundRelay.class);
  private final PreShipReview review = new PreShipReview(state, changes, panel, coverage, relay);

  private final TaskState handedBack =
      TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
  private final Finding leak = new Finding("A.java", "leaks", "", "wrong", "close()", "resource");
  private final Finding taste = new Finding("A.java", "naming", "", "noise", "x", "taste");

  @Test
  void relaysEachFindingToTheSessionAsReviewRoundWhenItHandsBack() {
    when(state.tasks()).thenReturn(Map.of("ABC-42", handedBack));
    when(changes.state(handedBack)).thenReturn(Optional.of("t1"));
    when(changes.diff(handedBack)).thenReturn("+line1\n");
    when(panel.review(any(), any(), any(), anyString()))
        .thenReturn(new Judgement("", "ready", List.of(leak, taste), "", List.of(), ""));
    when(relay.relay(any(), any(), any(), any(), any()))
        .thenReturn(new SweepResult(SweepResult.Kind.RELAYED, "relayed"));

    review.run("");

    ArgumentCaptor<ReviewFacts> round = ArgumentCaptor.captor();
    verify(relay).relay(eq("ABC-42"), any(), round.capture(), any(), eq("t1"));
    assertThat(round.getValue().discussion().threads())
        .containsExactly("code-reviewer: `A.java`: leaks");
    verify(coverage).raise("ABC-42", List.of(leak));
  }

  @Test
  void relaysNothingAndMarksTheTreeReadWhenTheDiffIsClean() {
    when(state.tasks()).thenReturn(Map.of("ABC-42", handedBack));
    when(changes.state(handedBack)).thenReturn(Optional.of("t2"));
    when(changes.diff(handedBack)).thenReturn("+line2\n");
    when(panel.review(any(), any(), any(), anyString()))
        .thenReturn(new Judgement("", "ready", List.of(), "", List.of(), ""));

    review.run("");

    verifyNoInteractions(relay);
    verify(state).reduce(eq("ABC-42"), any());
  }

  @Test
  void asksNoModelForTreeAlreadyRead() {
    TaskState read =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .told(ToldOnce.none().with(ToldOnce.Kind.PRE_SHIP_TREE, "t3"))
            .build();
    when(state.tasks()).thenReturn(Map.of("ABC-42", read));
    when(changes.state(read)).thenReturn(Optional.of("t3"));

    review.run("");

    verifyNoInteractions(panel, relay);
  }

  @Test
  void asksNoModelOnceRequestExists() {
    TaskState shipped =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .mrUrl("https://host/mr/9")
            .build();
    when(state.tasks()).thenReturn(Map.of("ABC-42", shipped));

    review.run("");

    verifyNoInteractions(panel, relay);
  }

  @Test
  void asksNoModelWhileTheSessionStillWorks() {
    TaskState working = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
    when(state.tasks()).thenReturn(Map.of("ABC-42", working));

    review.run("");

    verifyNoInteractions(panel, relay);
  }

  @Test
  void readsTheTreeAgainWhenTheReviewFailed() {
    when(state.tasks()).thenReturn(Map.of("ABC-42", handedBack));
    when(changes.state(handedBack)).thenReturn(Optional.of("t6"));
    when(changes.diff(handedBack)).thenReturn("+line6\n");
    when(panel.review(any(), any(), any(), anyString())).thenReturn(Judgement.failed("no model"));

    review.run("");

    verifyNoInteractions(relay);
    verify(state, never()).reduce(any(), any());
  }

  @Test
  void readsTheTreeAgainWhenTheRoundWasNotRelayed() {
    when(state.tasks()).thenReturn(Map.of("ABC-42", handedBack));
    when(changes.state(handedBack)).thenReturn(Optional.of("t7"));
    when(changes.diff(handedBack)).thenReturn("+line7\n");
    when(panel.review(any(), any(), any(), anyString()))
        .thenReturn(new Judgement("", "ready", List.of(leak), "", List.of(), ""));
    when(relay.relay(any(), any(), any(), any(), any()))
        .thenReturn(new SweepResult(SweepResult.Kind.STOPPED, "stopped"));

    review.run("");

    verify(state, never()).reduce(any(), any());
    verify(coverage, never()).raise(any(), any());
  }
}
