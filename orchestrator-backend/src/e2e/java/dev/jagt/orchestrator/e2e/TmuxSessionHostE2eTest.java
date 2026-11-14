package dev.jagt.orchestrator.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.tmux.TmuxReads;
import dev.jagt.orchestrator.adapter.tmux.TmuxSessionHost;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.port.TerminalDriver;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TmuxSessionHostE2eTest {

  private static final String SESSION =
      "jagt-host-e2e-" + UUID.randomUUID().toString().substring(0, 8);

  private final Processes processes = spy(new ProcessRunner());
  private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

  private final List<String> listWindows =
      List.of("tmux", "list-windows", "-t", "=" + SESSION, "-F", "#{window_name}");

  @TempDir Path root;

  private SessionHost host;

  @BeforeEach
  void startHostInTheTempRoot() {
    when(agentRuntime.launchCommand(any(), anyBoolean())).thenReturn("sleep 30");
    OrchestratorProperties orchestrator =
        OrchestratorProperties.defaults().withRoot(root.toString());
    host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, ToolchainProperties.defaults().withTmuxCommand("tmux")),
            new OrchestratorPaths(orchestrator),
            mock(TerminalDriver.class),
            agentRuntime);
  }

  @AfterEach
  void killTheThrowawaySession() {
    processes.run(
        null, Duration.ofSeconds(10), List.of("tmux", "kill-session", "-t", "=" + SESSION));
  }

  @Test
  void leavesOneWindowPerTaskWhenTheSameTaskIsStartedTwice() {
    host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false, "");
    host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false, "");

    assertThat(processes.run(null, Duration.ofSeconds(10), listWindows).stdout().lines().toList())
        .containsOnlyOnce("ABC-1");
  }

  @Test
  void killsOnlyTheNamedTasksWindowsAndSaysHowMany() {
    host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false, "");
    host.openTaskWindow(SESSION, "jagt", "ABC-2", "a2", root, false, "");

    int killed = host.killTaskWindows(SESSION, "ABC-1");

    assertThat(killed).isEqualTo(1);
    assertThat(processes.run(null, Duration.ofSeconds(10), listWindows).stdout().lines().toList())
        .contains("ABC-2")
        .doesNotContain("ABC-1");
  }

  @Test
  void keepsOneFeedWindowFollowingTheFileHoweverOftenItIsKept() {
    host.keepFeedWindow(SESSION, "master", root.resolve("feed.log"));
    host.keepFeedWindow(SESSION, "master", root.resolve("feed.log"));

    assertThat(processes.run(null, Duration.ofSeconds(10), listWindows).stdout().lines().toList())
        .containsOnlyOnce("master");
  }

  @Test
  void keepsFeedWindowThatIsAlreadyThereInThreeCommands() {
    host.keepFeedWindow(SESSION, "master", root.resolve("feed.log"));
    clearInvocations(processes);

    host.keepFeedWindow(SESSION, "master", root.resolve("feed.log"));

    verify(processes, times(3)).run(any(), any(), anyList());
  }

  @Test
  void readsRunningAgentInTwoCommands() {
    host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false, "");
    clearInvocations(processes);

    SessionHost.WindowState state = host.taskWindowState(SESSION, "ABC-1");

    assertThat(state).isEqualTo(SessionHost.WindowState.AGENT_RUNNING);
    verify(processes, times(2)).run(any(), any(), anyList());
  }

  @Test
  void readsWindowsLastActivityInOneCommand() {
    host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false, "");
    clearInvocations(processes);

    long activity = host.lastWindowActivityMillis(SESSION, "ABC-1");

    assertThat(activity).isPositive();
    verify(processes, times(1)).run(any(), any(), anyList());
  }

  @Test
  void reportsWindowThatWasNeverOpenedAsMissing() {
    host.openTaskWindow(SESSION, "jagt", "ABC-1", "a1", root, false, "");

    assertThat(host.taskWindowState(SESSION, "ABC-9")).isEqualTo(SessionHost.WindowState.MISSING);
  }

  @Test
  void showsLongTaskNameCutToTenCharactersWhileTheWindowKeepsItWhole() {
    host.openTaskWindow(SESSION, "jagt", "abcservice-1234567890-xyz", "a1", root, false, "");

    String shown =
        processes
            .run(
                null,
                Duration.ofSeconds(10),
                List.of(
                    "tmux",
                    "display-message",
                    "-p",
                    "-t",
                    "=" + SESSION + ":",
                    "#{E:window-status-current-format}"))
            .stdout()
            .strip();

    assertThat(shown).contains("abcservice… (a1)").doesNotContain("1234567890");
    assertThat(processes.run(null, Duration.ofSeconds(10), listWindows).stdout().lines().toList())
        .contains("abcservice-1234567890-xyz");
  }
}
