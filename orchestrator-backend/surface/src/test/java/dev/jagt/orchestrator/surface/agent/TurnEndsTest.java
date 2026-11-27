package dev.jagt.orchestrator.surface.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.service.turn.TurnEndVerdict;
import dev.jagt.orchestrator.service.turn.TurnEndVerdicts;
import dev.jagt.orchestrator.service.turn.TurnEnding;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;

class TurnEndsTest {

  private final AgentRuntime runtime = mock(AgentRuntime.class);
  private final TurnEndVerdicts verdicts = mock(TurnEndVerdicts.class);
  private final TurnEnds turnEnds = new TurnEnds(runtime, verdicts);
  private final TaskState task = TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build();

  @Test
  void sendsRefusalOnInTheWordsTheCliReadsAsBlock() {
    when(verdicts.of("ABC-1", task, new TurnEnding(false, false, null)))
        .thenReturn(new TurnEndVerdict.Refuse("report first"));
    when(runtime.refusedTurnEnd("report first")).thenReturn("{\"decision\": \"block\"}");

    assertThat(turnEnds.answer("ABC-1", task, false, false, null))
        .isEqualTo("{\"decision\": \"block\"}");
  }

  @Test
  void tellsTheHumanRoleNoteInTheWordsTheCliReadsAsMessage() {
    when(verdicts.of("ABC-1", task, new TurnEnding(false, false, null)))
        .thenReturn(new TurnEndVerdict.Tell("with the role"));
    when(runtime.toldTheHuman("with the role")).thenReturn("{\"systemMessage\": \"x\"}");

    assertThat(turnEnds.answer("ABC-1", task, false, false, null))
        .isEqualTo("{\"systemMessage\": \"x\"}");
  }

  @Test
  void saysNothingWhenTheTurnEndsSilent() {
    when(verdicts.of("ABC-1", task, new TurnEnding(false, false, null)))
        .thenReturn(new TurnEndVerdict.Silent());

    assertThat(turnEnds.answer("ABC-1", task, false, false, null)).isEmpty();
  }
}
