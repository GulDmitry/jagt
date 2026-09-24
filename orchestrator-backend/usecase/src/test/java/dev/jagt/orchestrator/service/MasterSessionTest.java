package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.MasterConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterSessionTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final SessionHost sessions = mock(SessionHost.class);
    private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

    private MasterSession session(MasterConfig master) {
        when(configService.load()).thenReturn(ConfigFile.defaults().withMaster(master));
        when(sessions.sessionName(any())).thenReturn("jagt");
        return new MasterSession(configService, sessions, agentRuntime,
                new OrchestratorPaths(OrchestratorProperties.defaults().withRoot("/repo")));
    }

    @Test
    void startsNothingWhileNobodyHasAskedForIt() {
        boolean started = session(MasterConfig.defaults()).startIfWanted();

        assertThat(started).isFalse();
        verify(sessions, never()).openWindow(anyString(), any(), anyString(), any(), anyString());
    }

    @Test
    void runsItInTheOrchestratorRootRatherThanAnyTasksWorktree() {
        when(agentRuntime.launchCommand(any(), anyBoolean(), anyString())).thenReturn("claude --model fable");

        boolean started = session(new MasterConfig("judge", null, "fable", null)).startIfWanted();

        assertThat(started).isTrue();
        verify(sessions).openWindow(eq("jagt"), any(), eq("master"), eq(Path.of("/repo")),
                eq("claude --model fable"));
    }

    @Test
    void leavesASessionThatIsAlreadyRunningAlone() {
        when(sessions.taskWindowState("jagt", "master"))
                .thenReturn(SessionHost.WindowState.AGENT_RUNNING);

        boolean started = session(new MasterConfig("judge", null, null, null)).startIfWanted();

        assertThat(started).isFalse();
        verify(sessions, never()).openWindow(anyString(), any(), anyString(), any(), anyString());
    }
}
