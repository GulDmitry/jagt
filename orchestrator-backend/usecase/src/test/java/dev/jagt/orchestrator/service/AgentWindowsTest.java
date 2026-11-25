package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.view.Refusal;
import dev.jagt.orchestrator.service.session.SessionHosting;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentWindowsTest {

  private final StateService state = mock(StateService.class);
  private final SessionHosting hosting = mock(SessionHosting.class);
  private final AgentWindows windows =
      new AgentWindows(
          mock(ConfigService.class),
          state,
          hosting,
          vetted -> "credential check 'npm registry' fails");

  @Test
  void noSessionStartsWhileProjectCredentialFails() {
    TaskState task = TaskState.builder("shop", "/wt", TaskStatus.NEW).build();
    when(state.task("ABC-42")).thenReturn(Optional.of(task));

    assertThatThrownBy(() -> windows.openTab("ABC-42", "a1", Path.of("/wt"), false, null))
        .isInstanceOf(Refusal.class)
        .hasMessageContaining("npm registry");
    verifyNoInteractions(hosting);
  }
}
