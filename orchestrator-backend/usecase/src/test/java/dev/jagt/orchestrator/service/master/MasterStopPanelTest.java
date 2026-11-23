package dev.jagt.orchestrator.service.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.assistant.RoundReviewer.Finding;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.service.ConfigFile.ArbiterConfig;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.stop.TaskStop;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MasterStopPanelTest {

  private final ChargedReviews reviews = mock(ChargedReviews.class);
  private final MasterBriefs briefs = mock(MasterBriefs.class);
  private final MasterStopPanel panel = new MasterStopPanel(reviews, briefs);
  private final ArbiterConfig config = ArbiterConfig.defaults();
  private final TaskState stopped =
      TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
          .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 5, "verify sent 3 rounds"))
          .build();

  @Test
  void answersWithWhatTheMastersFirstFindingSays() {
    when(briefs.master("ABC-1", config)).thenReturn(Optional.of("the brief"));
    Finding said = new Finding("", "lift: each round answered a new report", "", "", "", "");
    when(reviews.review(eq("ABC-1"), any()))
        .thenReturn(new Judgement("", "ready", List.of(said), "", List.of(), ""));

    Optional<MasterStopAnswer> answer = panel.ask("ABC-1", stopped, config);

    assertThat(answer)
        .contains(
            new MasterStopAnswer(MasterStopAnswer.Kind.LIFT, "each round answered a new report"));
  }

  @Test
  void leavesTheStopToTheHumanWhereTheReviewFailed() {
    when(briefs.master("ABC-1", config)).thenReturn(Optional.of("the brief"));
    when(reviews.review(eq("ABC-1"), any())).thenReturn(Judgement.failed("no answer"));

    assertThat(panel.ask("ABC-1", stopped, config)).isEmpty();
  }

  @Test
  void asksNothingWhereTheTaskHoldsNoStop() {
    TaskState running = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();
    when(briefs.master("ABC-1", config)).thenReturn(Optional.of("the brief"));

    assertThat(panel.ask("ABC-1", running, config)).isEmpty();
    verifyNoInteractions(reviews);
  }
}
