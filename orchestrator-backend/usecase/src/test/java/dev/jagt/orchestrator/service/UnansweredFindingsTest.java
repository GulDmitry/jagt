package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.service.worktree.WorktreeChanges;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.RoundTrail;
import dev.jagt.orchestrator.task.turn.ToldOnce;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnansweredFindingsTest {

  private final StateService state = mock(StateService.class);
  private final HandBackDue handBackDue = mock(HandBackDue.class);
  private final CodeReviewSettings settings = mock(CodeReviewSettings.class);
  private final WorktreeChanges changes = mock(WorktreeChanges.class);
  private final UnansweredFindings findings =
      new UnansweredFindings(state, handBackDue, settings, changes);
  private final RoundTrail.Round relayed = new RoundTrail.Round(1_000, 2, "", "a", "n1");

  @Test
  void namesTheFindingsAwaitingTheSessionsBlockBeforeAnyRequestExists() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(state.task("ABC-42")).thenReturn(Optional.of(task));
    when(handBackDue.unanswered(task)).thenReturn(Optional.of(relayed));

    assertThat(findings.awaiting("ABC-42"))
        .hasValueSatisfying(sentence ->
            assertThat(sentence).contains("2 finding(s)", "review_replies.md", "then ship"));
  }

  @Test
  void namesTheFindingsAwaitingTheSessionsBlockOnTaskThatHoldsRequest() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .mrUrl("https://host/mr/9")
            .build();
    when(state.task("ABC-42")).thenReturn(Optional.of(task));
    when(handBackDue.unanswered(task)).thenReturn(Optional.of(relayed));

    assertThat(findings.awaiting("ABC-42"))
        .hasValueSatisfying(sentence -> assertThat(sentence).contains("2 finding(s)"));
  }

  @Test
  void declinesTheShipOfTaskThatHoldsRequestUntilTheReviewReadItsTree() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .mrUrl("https://host/mr/9")
            .build();
    when(state.task("ABC-42")).thenReturn(Optional.of(task));
    when(handBackDue.unanswered(task)).thenReturn(Optional.empty());
    when(settings.load()).thenReturn(CodeReviewSettings.Settings.defaults());
    when(changes.state(task)).thenReturn(Optional.of("t1"));

    assertThat(findings.awaiting("ABC-42"))
        .hasValueSatisfying(sentence -> assertThat(sentence).contains("has not read this change"));
  }

  @Test
  void namesTheReadTheShipWaitsFor() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(state.task("ABC-42")).thenReturn(Optional.of(task));
    when(settings.load()).thenReturn(CodeReviewSettings.Settings.defaults());
    when(changes.state(task)).thenReturn(Optional.of("t1"));

    assertThat(findings.awaiting("ABC-42"))
        .hasValueSatisfying(sentence -> assertThat(sentence).contains("has not read this change"));
  }

  @ParameterizedTest
  @CsvSource({
    "REVIEW_PENDING, PRE_SHIP_TREE, none, t1, true, true",
    "REVIEW_PENDING, PRE_SHIP_TREE, t1, t2, true, true",
    "REVIEW_PENDING, PRE_SHIP_TREE, t1, t1, true, false",
    "REVIEW_PENDING, PRE_SHIP_FAILED, t1, t1, true, false",
    "REVIEW_PENDING, PRE_SHIP_TREE, none, t1, false, false",
    "IN_PROGRESS, PRE_SHIP_TREE, none, t1, true, false"
  })
  void declinesTheShipUntilTheReviewReadTheCurrentTree(
      TaskStatus status,
      ToldOnce.Kind kind,
      String toldTree,
      String currentTree,
      boolean enabled,
      boolean declined) {
    TaskState task =
        TaskState.builder("proj", "/wt", status).told(ToldOnce.none().with(kind, toldTree)).build();
    when(state.task("ABC-42")).thenReturn(Optional.of(task));
    when(handBackDue.unanswered(task)).thenReturn(Optional.empty());
    when(settings.load()).thenReturn(new CodeReviewSettings.Settings(enabled, false, 5000, 1.0));
    when(changes.state(task)).thenReturn(Optional.of(currentTree));

    assertThat(findings.awaiting("ABC-42").isPresent()).isEqualTo(declined);
  }
}
