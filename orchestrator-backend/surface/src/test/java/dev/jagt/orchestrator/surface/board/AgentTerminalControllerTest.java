package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.port.WebTerminal;
import dev.jagt.orchestrator.service.AgentSessions;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTerminalControllerTest {

    private final AgentSessions sessions = mock(AgentSessions.class);
    private final WebTerminal webTerminal = mock(WebTerminal.class);
    private final AgentTerminalController api = new AgentTerminalController(sessions, webTerminal);

    @Test
    void handsBackThePortServingTheSessionTheTaskRunsIn() {
        when(sessions.sessionOf("ABC-1")).thenReturn("jagt");
        when(webTerminal.serve("jagt")).thenReturn(OptionalInt.of(8291));

        assertThat(api.terminal("ABC-1").port()).isEqualTo(8291);
    }

    @Test
    void answersWithNoPortWhenNoWebTerminalIsConfigured() {
        when(sessions.sessionOf("ABC-1")).thenReturn("jagt");
        when(webTerminal.serve("jagt")).thenReturn(OptionalInt.empty());

        assertThat(api.terminal("ABC-1").port()).isNull();
    }
}
