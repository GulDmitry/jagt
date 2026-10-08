package dev.jagt.orchestrator.e2e;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.tmux.TmuxSessionHost;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.port.TerminalDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TmuxSessionHostE2eTest {

    private static final String SESSION = "jagt-host-e2e-" + UUID.randomUUID().toString().substring(0, 8);

    private final Processes processes = new ProcessRunner();
    private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

    private TmuxSessionHost hostIn(Path root) {
        when(agentRuntime.launchCommand(any(), anyBoolean())).thenReturn("sleep 30");
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withTmuxCommand("tmux");
        return new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agentRuntime);
    }

    @AfterEach
    void killTheThrowawaySession() {
        processes.run(null, Duration.ofSeconds(10), List.of("tmux", "kill-session", "-t", "=" + SESSION));
    }

    @Test
    void leavesOneWindowPerTaskWhenTheSameTaskIsStartedTwice(@TempDir Path root) {
        SessionHost host = hostIn(root);

        host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false);
        host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false);

        assertThat(windowNames()).containsOnlyOnce("ABC-1");
    }

    @Test
    void killsOnlyTheNamedTasksWindowsAndSaysHowMany(@TempDir Path root) {
        SessionHost host = hostIn(root);
        host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false);
        host.openTaskWindow(SESSION, "jagt", "ABC-2", "a2", root, false);

        int killed = host.killTaskWindows(SESSION, "ABC-1");

        assertThat(killed).isEqualTo(1);
        assertThat(windowNames()).contains("ABC-2").doesNotContain("ABC-1");
    }

    @Test
    void keepsOneFeedWindowFollowingTheFileHoweverOftenItIsKept(@TempDir Path root) {
        SessionHost host = hostIn(root);

        host.keepFeedWindow(SESSION, "master", root.resolve("feed.log"));
        host.keepFeedWindow(SESSION, "master", root.resolve("feed.log"));

        assertThat(windowNames()).containsOnlyOnce("master");
    }

    @Test
    void reportsAWindowThatWasNeverOpenedAsMissing(@TempDir Path root) {
        SessionHost host = hostIn(root);
        host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false);

        assertThat(host.taskWindowState(SESSION, "ABC-9")).isEqualTo(SessionHost.WindowState.MISSING);
    }

    @Test
    void showsALongTaskNameCutToTenCharactersWhileTheWindowKeepsItWhole(@TempDir Path root) {
        SessionHost host = hostIn(root);
        host.openTaskWindow(SESSION, "jagt", "abcservice-1234567890-xyz", "a1", root, false);

        String shown = processes.run(null, Duration.ofSeconds(10), List.of("tmux", "display-message",
                "-p", "-t", "=" + SESSION + ":", "#{E:window-status-current-format}")).stdout().strip();

        assertThat(shown).contains("abcservice… (a1)").doesNotContain("1234567890");
        assertThat(windowNames()).contains("abcservice-1234567890-xyz");
    }

    private List<String> windowNames() {
        return processes.run(null, Duration.ofSeconds(10), List.of("tmux", "list-windows",
                        "-t", "=" + SESSION, "-F", "#{window_name}"))
                .stdout().lines().toList();
    }
}
