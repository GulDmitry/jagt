package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.Composition;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.session.SessionPhase;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.session.Session;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class SessionPresenceTest {

  private static final TaskReducer reducer = new TaskReducer(new TaskChart(Composition.builtIn()));

  @TempDir Path root;

  private OrchestratorProperties properties;
  private StateService state;
  private final SessionProbe probe = mock(SessionProbe.class);

  @BeforeEach
  void putsShippingTask() {
    properties =
        OrchestratorProperties.defaults()
            .withRoot(root.toString())
            .withStateFile(root.resolve("state.json").toString());
    state =
        new StateService(new JsonMapper(), new OrchestratorPaths(properties), reducer, event -> {});
    state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build());
  }

  @Test
  void answersLiveFromTheWindowTheReadFound() {
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 300_000, null));

    assertThat(new SessionPresence(state, probe, properties, Clock.systemUTC()).agentLive("ABC-1"))
        .isTrue();
  }

  @Test
  void answersNotLiveAndRecordsTheEndWhereTheWindowHoldsNoAgent() {
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenReturn(
            new SessionSign.Read(Session.Window.State.DEAD_SHELL, 0, 0, 0, 300_000, "exit 1"));

    boolean live =
        new SessionPresence(state, probe, properties, Clock.systemUTC()).agentLive("ABC-1");

    assertThat(live).isFalse();
    assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session()))
        .isEqualTo(SessionPhase.GONE);
  }

  @Test
  void saysItCannotTellWhereTheWindowsCannotBeListed() {
    when(probe.read(anyString(), any(), anyLong(), anyLong()))
        .thenThrow(new SessionHost.WindowsUnreadable("tmux timed out"));
    SessionPresence presence = new SessionPresence(state, probe, properties, Clock.systemUTC());

    assertThatThrownBy(() -> presence.agentLive("ABC-1")).isInstanceOf(AgentPresence.Unread.class);
  }
}
