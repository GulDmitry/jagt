package dev.jagt.orchestrator.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.Move;
import dev.jagt.orchestrator.flow.Owner;
import dev.jagt.orchestrator.flow.RoundState;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.service.HandBack;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

class NextMoveTest {

  @Test
  void leavesTheMoveToTheAgentWhileItIsWorking() {
    HandBack handBack = mock(HandBack.class);
    TaskState task = TaskState.builder("api", "/wt/ABC-42-api", TaskStatus.IN_PROGRESS).build();
    when(handBack.round(task)).thenReturn(RoundState.NONE);

    Move move =
        new NextMove(handBack, new FlowRules(new TaskChart(Composition.builtIn()))).of(task);

    assertThat(move.owner()).isEqualTo(Owner.AGENT);
  }
}
