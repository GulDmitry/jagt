package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.review.RoundTrail;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnansweredFindingsTest {

  private final StateService state = mock(StateService.class);
  private final HandBackDue handBackDue = mock(HandBackDue.class);
  private final UnansweredFindings findings = new UnansweredFindings(state, handBackDue);
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
  void awaitsNothingOnceTheSessionAnsweredEveryFinding() {
    TaskState task = TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING).build();
    when(state.task("ABC-42")).thenReturn(Optional.of(task));
    when(handBackDue.unanswered(task)).thenReturn(Optional.empty());

    assertThat(findings.awaiting("ABC-42")).isEmpty();
  }

  @Test
  void awaitsNothingOnTaskThatHoldsRequest() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.REVIEW_PENDING)
            .mrUrl("https://host/mr/9")
            .build();
    when(state.task("ABC-42")).thenReturn(Optional.of(task));
    when(handBackDue.unanswered(task)).thenReturn(Optional.of(relayed));

    assertThat(findings.awaiting("ABC-42")).isEmpty();
  }
}
