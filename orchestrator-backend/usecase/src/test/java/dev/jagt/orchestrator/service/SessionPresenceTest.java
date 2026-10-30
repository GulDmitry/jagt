package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.flow.SessionPhase;
import dev.jagt.orchestrator.flow.SessionSign;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.task.Session;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionPresenceTest {

    @Test
    void answersLiveFromTheWindowTheReadFound(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.RUNNING, 0, 0, 0, 300_000, null));

        assertThat(new SessionPresence(state, probe, properties, Clock.systemUTC()).agentLive("ABC-1")).isTrue();
    }

    @Test
    void answersNotLiveAndRecordsTheEndWhereTheWindowHoldsNoAgent(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenReturn(new SessionSign.Read(Session.Window.State.DEAD_SHELL, 0, 0, 0, 300_000, "exit 1"));

        boolean live = new SessionPresence(state, probe, properties, Clock.systemUTC()).agentLive("ABC-1");

        assertThat(live).isFalse();
        assertThat(SessionPhase.of(state.task("ABC-1").orElseThrow().session())).isEqualTo(SessionPhase.GONE);
    }

    @Test
    void saysItCannotTellWhereTheWindowsCannotBeListed(@TempDir Path root) {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        StateService state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.SHIPPING).build());
        SessionProbe probe = mock(SessionProbe.class);
        when(probe.read(anyString(), any(), anyLong(), anyLong()))
                .thenThrow(new SessionHost.WindowsUnreadable("tmux timed out"));
        SessionPresence presence = new SessionPresence(state, probe, properties, Clock.systemUTC());

        assertThatThrownBy(() -> presence.agentLive("ABC-1")).isInstanceOf(AgentPresence.Unread.class);
    }
}
