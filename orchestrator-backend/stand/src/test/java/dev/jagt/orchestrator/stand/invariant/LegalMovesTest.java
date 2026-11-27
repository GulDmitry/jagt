package dev.jagt.orchestrator.stand.invariant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

class LegalMovesTest {

  private final LegalMoves moves =
      new LegalMoves(new FlowRules(new TaskChart(Composition.builtIn())));

  @Test
  void allowsRedReadToStopTaskWaitingOnTheHost() {
    assertThat(moves.allows(TaskStatus.CI_POLLING, TaskStatus.CI_FAILED)).isTrue();
  }

  @Test
  void deniesReviewedToTaskNoReadMovesBecauseItsSessionIsAtWork() {
    assertThat(moves.allows(TaskStatus.IN_PROGRESS, TaskStatus.REVIEWED)).isFalse();
  }
}
