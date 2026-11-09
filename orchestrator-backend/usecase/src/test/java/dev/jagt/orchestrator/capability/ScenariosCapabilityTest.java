package dev.jagt.orchestrator.capability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.ScenarioSessions;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TicketTexts;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScenariosCapabilityTest {

  private final ScenarioSessions sessions = mock(ScenarioSessions.class);
  private final TicketTexts tickets = mock(TicketTexts.class);
  private final StateService state = mock(StateService.class);
  private final ScenariosCapability capability = new ScenariosCapability(sessions, tickets, state);

  @Test
  void opensTheProjectsSessionWithTheTicketLoaded() {
    when(state.task("ABC-42"))
        .thenReturn(Optional.of(TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS).build()));
    when(tickets.of("ABC-42")).thenReturn(Optional.of("Refund retries twice"));

    capability.run("ABC-42");

    verify(sessions)
        .open("alpha", "Task ABC-42.\n<ticket>\nRefund retries twice\n</ticket>");
  }

  @Test
  void saysTheTicketIsNotReadWhereNoTextIsKept() {
    when(state.task("ABC-42"))
        .thenReturn(Optional.of(TaskState.builder("alpha", "/wt", TaskStatus.IN_PROGRESS).build()));
    when(tickets.of("ABC-42")).thenReturn(Optional.empty());

    capability.run("ABC-42");

    verify(sessions).open("alpha", "Task ABC-42.\nThe ticket text of ABC-42 is not read yet.");
  }

  @Test
  void refusesATaskWithNoProject() {
    when(state.task("ABC-42"))
        .thenReturn(Optional.of(TaskState.builder(null, "/wt", TaskStatus.NEW).build()));

    assertThatThrownBy(() -> capability.run("ABC-42")).isInstanceOf(Refusal.class);
    verifyNoInteractions(sessions);
  }
}
