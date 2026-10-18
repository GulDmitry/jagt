package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.TerminalDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TmuxSessionHostTest {

    @ParameterizedTest
    @ValueSource(strings = {"jagt:agents", "jagt.agents"})
    void refusesASessionNameTmuxWouldReadAsAWindowOrAPane(String name) {
        assertThat(TmuxSessionHost.reserved(name)).contains("contains ':' or '.', which tmux reserves");
    }

    @Test
    void throwsRatherThanReportingNoWindowWhenTheWindowIsThereButItsScreenCannotBeRead() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@1 ABC-1\n", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("capture-pane")))).thenReturn(new Processes.Result(1, "", "can't find pane"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThatThrownBy(() -> host.screenOf("jagt", "ABC-1")).isInstanceOf(IllegalStateException.class);
    }
}
