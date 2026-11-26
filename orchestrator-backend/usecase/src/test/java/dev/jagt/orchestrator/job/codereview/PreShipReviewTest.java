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
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Verdict;
import dev.jagt.orchestrator.service.RoundRelay;
import dev.jagt.orchestrator.service.RoundSource;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.review.ReviewSweepService.SweepResult;
import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.ReviewFacts;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.util.List;
import java.util.Map;
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
    when(changes.reviewKey(handedBack)).thenReturn("t1");
    when(changes.diff(handedBack)).thenReturn("+line1\n");
    when(panel.review(any(), any(), any(), anyString()))
        .thenReturn(
            new Judgement("", Verdict.READY, List.of(leak, taste), "", List.of(), "", Press.NONE));
    when(relay.relay(any(), any(), any(), any(), any(), any()))
        .thenReturn(new SweepResult(SweepResult.Kind.RELAYED, "relayed"));

    review.run("");

    ArgumentCaptor<ReviewFacts> round = ArgumentCaptor.captor();
    verify(relay)
        .relay(eq("ABC-42"), eq(RoundSource.TREE), any(), round.capture(), any(), eq("t1"));
    assertThat(round.getValue().discussion().threads())
        .containsExactly("reviewer: `A.java`: leaks");
    verify(coverage).raise("ABC-42", List.of(leak));
  }

  @Test
  void relaysNothingAndMarksTheTreeReadWhenTheDiffIsClean() {
    when(state.tasks()).thenReturn(Map.of("ABC-42", handedBack));
    when(changes.reviewKey(handedBack)).thenReturn("t2");
    when(changes.diff(handedBack)).thenReturn("+line2\n");
    when(panel.review(any(), any(), any(), anyString()))
        .thenReturn(new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE));

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
    when(changes.reviewKey(read)).thenReturn("t3");

    review.run("");

    verifyNoInteractions(panel, relay);
  }

  @Test
  void readsTheTreeOfTaskThatHoldsRequestBeforeItsNextPush() {
    TaskState shipped =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .mrUrl("https://host/mr/9")
            .build();
    when(state.tasks()).thenReturn(Map.of("ABC-42", shipped));
    when(changes.reviewKey(shipped)).thenReturn("t9");
    when(changes.diff(shipped)).thenReturn("+line9\n");
    when(panel.review(any(), any(), any(), anyString()))
        .thenReturn(new Judgement("", Verdict.READY, List.of(), "", List.of(), "", Press.NONE));

    review.run("");

    verify(panel).review(any(), any(), any(), anyString());
  }

  @Test
  void asksNoModelWhileTheSessionStillWorks() {
    TaskState working = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
    when(state.tasks()).thenReturn(Map.of("ABC-42", working));

    review.run("");

    verifyNoInteractions(panel, relay);
  }

  @Test
  void marksTheTreeReadWhenItsDiffIsEmpty() {
    when(state.tasks()).thenReturn(Map.of("ABC-42", handedBack));
    when(changes.reviewKey(handedBack)).thenReturn("t8");
    when(changes.diff(handedBack)).thenReturn("");

    review.run("");

    verifyNoInteractions(panel, relay);
    verify(state).reduce(eq("ABC-42"), any());
  }

  @Test
  void recordsTheFailureSoNoShipWaitsOnItAndReadsTheTreeAgain() {
    when(state.tasks()).thenReturn(Map.of("ABC-42", handedBack));
    when(changes.reviewKey(handedBack)).thenReturn("t6");
    when(changes.diff(handedBack)).thenReturn("+line6\n");
    when(panel.review(any(), any(), any(), anyString())).thenReturn(Judgement.failed("no model"));

    review.run("");

    verifyNoInteractions(relay);
    verify(state).reduce(eq("ABC-42"), any());
  }

  @Test
  void readsTheTreeAgainWhenTheRoundWasNotRelayed() {
    when(state.tasks()).thenReturn(Map.of("ABC-42", handedBack));
    when(changes.reviewKey(handedBack)).thenReturn("t7");
    when(changes.diff(handedBack)).thenReturn("+line7\n");
    when(panel.review(any(), any(), any(), anyString()))
        .thenReturn(new Judgement("", Verdict.READY, List.of(leak), "", List.of(), "", Press.NONE));
    when(relay.relay(any(), any(), any(), any(), any(), any()))
        .thenReturn(new SweepResult(SweepResult.Kind.STOPPED, "stopped"));

    review.run("");

    verify(state, never()).reduce(any(), any());
    verify(coverage, never()).raise(any(), any());
  }
}
