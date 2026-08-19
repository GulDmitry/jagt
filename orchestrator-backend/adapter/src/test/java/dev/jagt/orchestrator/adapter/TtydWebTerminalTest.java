package dev.jagt.orchestrator.adapter;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.WebTerminalProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.ServerSocket;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("loopback-ports")
class TtydWebTerminalTest {

    private final ProcessRunner processes = mock(ProcessRunner.class);

    @Test
    void refusesToStartWhenTheTerminalIsEnabledAndNothingServesIt() {
        WebTerminalProperties enabled = WebTerminalProperties.defaults().withEnabled(true)
                .withCommand("no-such-ttyd");

        assertThat(new TtydWebTerminal(processes, enabled, OrchestratorProperties.defaults()).problems())
                .singleElement(STRING)
                .contains("orchestrator.web-terminal.command", "no-such-ttyd");
    }

    @Test
    void saysNothingAboutAMissingTerminalNobodyAskedFor() {
        WebTerminalProperties disabled = WebTerminalProperties.defaults().withCommand("no-such-ttyd");

        assertThat(new TtydWebTerminal(processes, disabled, OrchestratorProperties.defaults()).problems())
                .isEmpty();
    }

    @Test
    void attachesAWritableTerminalToTheTmuxSession() {
        assertThat(TtydWebTerminal.serveCommand("ttyd", "127.0.0.1", 8291, "tmux", "jagt"))
                .startsWith("ttyd", "--port", "8291")
                .contains("--writable")
                .containsSequence("--interface", "127.0.0.1")
                .endsWith("tmux", "attach", "-t", "jagt");
    }

    @Test
    void listensOnEveryInterfaceWhenNoAddressIsConfigured() {
        assertThat(TtydWebTerminal.serveCommand("ttyd", "", 8291, "tmux", "jagt"))
                .doesNotContain("--interface");
    }

    @Test
    void letsNoPageButItsOwnOpenASocketIntoTheSession() {
        assertThat(TtydWebTerminal.serveCommand("ttyd", "127.0.0.1", 8291, "tmux", "jagt"))
                .contains("--check-origin");
    }

    @Test
    void leavesNothingRunningOnceTheLastViewerDisconnects() {
        assertThat(TtydWebTerminal.serveCommand("ttyd", "127.0.0.1", 8291, "tmux", "jagt"))
                .contains("--exit-no-conn");
    }

    @Test
    void spawnsNothingWhenNoWebTerminalIsConfigured() {
        var terminal = new TtydWebTerminal(processes, WebTerminalProperties.defaults(),
                OrchestratorProperties.defaults());

        assertThat(terminal.serve("jagt")).isEmpty();
        verifyNoInteractions(processes);
    }

    @Test
    void answersWithThePortTheServerItStartedListensOn() throws Exception {
        int free;
        try (ServerSocket probe = new ServerSocket(0)) {
            free = probe.getLocalPort();
        }
        when(processes.runDetached(any(), anyList())).thenReturn(mock(Process.class));
        var terminal = new TtydWebTerminal(processes,
                WebTerminalProperties.defaults().withEnabled(true).withPort(free),
                OrchestratorProperties.defaults().withTmuxCommand("tmux"));

        assertThat(terminal.serve("jagt")).hasValue(free);
    }

    @Test
    void reusesTheRunningServerForASecondLookAtTheSameSession() {
        Process serving = mock(Process.class);
        when(serving.isAlive()).thenReturn(true);
        when(processes.runDetached(any(), anyList())).thenReturn(serving);
        var terminal = new TtydWebTerminal(processes, WebTerminalProperties.defaults().withEnabled(true),
                OrchestratorProperties.defaults().withTmuxCommand("tmux"));

        terminal.serve("jagt");
        terminal.serve("jagt");

        verify(processes, times(1)).runDetached(any(), anyList());
    }

    @Test
    void doesNotHandOutAPortSomethingElseIsAlreadyHolding() throws Exception {
        try (ServerSocket taken = new ServerSocket(0)) {
            when(processes.runDetached(any(), anyList())).thenReturn(mock(Process.class));
            var terminal = new TtydWebTerminal(processes,
                    WebTerminalProperties.defaults().withEnabled(true).withPort(taken.getLocalPort()),
                    OrchestratorProperties.defaults().withTmuxCommand("tmux"));

            assertThat(terminal.serve("jagt")).isPresent().isNotEqualTo(OptionalInt.of(taken.getLocalPort()));
        }
    }

    @Test
    void namesTheLaunchItCouldNotMakeInsteadOfAnsweringWithNothing() {
        when(processes.runDetached(any(), anyList()))
                .thenThrow(new IllegalStateException("Failed to launch: ttyd --port 8291"));
        var terminal = new TtydWebTerminal(processes, WebTerminalProperties.defaults().withEnabled(true),
                OrchestratorProperties.defaults().withTmuxCommand("tmux"));

        assertThatThrownBy(() -> terminal.serve("jagt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to launch: ttyd");
    }

    @Test
    void answersWithNothingWhenTheServerDiesOnStartup() throws Exception {
        Process died = mock(Process.class);
        when(died.waitFor(anyLong(), any())).thenReturn(true);
        when(died.exitValue()).thenReturn(1);
        when(processes.runDetached(any(), anyList())).thenReturn(died);
        var terminal = new TtydWebTerminal(processes, WebTerminalProperties.defaults().withEnabled(true),
                OrchestratorProperties.defaults().withTmuxCommand("tmux"));

        assertThat(terminal.serve("jagt")).isEmpty();
    }
}
