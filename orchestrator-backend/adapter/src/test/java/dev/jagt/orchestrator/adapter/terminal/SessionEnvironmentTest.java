package dev.jagt.orchestrator.adapter.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.worktree.NoWorktreeContainers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionEnvironmentTest {

  private final StateService state = mock(StateService.class);
  private final SessionEnvironment environment =
      new SessionEnvironment(
          new OrchestratorPaths(OrchestratorProperties.defaults(), FakeHost.machine()),
          new NoWorktreeContainers(),
          FakeHost.machine(),
          state);

  @Test
  void startsSessionWithTheStartItBeganOnSoItsHooksCanNameIt() {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .build()
            .withSession(Session.defaults().withGeneration(2));
    when(state.findByWorktree("/wt")).thenReturn(Optional.of(Map.entry("ABC-1", task)));

    assertThat(environment.prefix(Path.of("/wt"))).contains("JAGT_SESSION_START=2 ");
  }

  @Test
  void startsSessionNoTaskHoldsWithoutStart() {
    when(state.findByWorktree("/elsewhere")).thenReturn(Optional.empty());

    assertThat(environment.prefix(Path.of("/elsewhere"))).doesNotContain("JAGT_SESSION_START");
  }
}
