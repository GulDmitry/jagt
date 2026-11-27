package dev.jagt.orchestrator.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.herdr.HerdrApi;
import dev.jagt.orchestrator.adapter.herdr.HerdrProperties;
import dev.jagt.orchestrator.adapter.herdr.HerdrReads;
import dev.jagt.orchestrator.adapter.herdr.HerdrSessionHost;
import dev.jagt.orchestrator.adapter.herdr.UnixHerdrWire;
import dev.jagt.orchestrator.adapter.host.Executables;
import dev.jagt.orchestrator.adapter.host.SystemHost;
import dev.jagt.orchestrator.adapter.terminal.SessionEnvironment;
import dev.jagt.orchestrator.adapter.worktree.NoWorktreeContainers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import dev.jagt.orchestrator.service.StateService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HerdrSessionHostE2eTest {

  private static final String SESSION =
      "jagt-host-e2e-" + UUID.randomUUID().toString().substring(0, 8);
  private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

  private final Processes processes = new ProcessRunner();
  private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

  @TempDir Path root;

  private SessionHost host;

  @BeforeAll
  static void herdrIsInstalled() {
    assumeThat(Executables.onPath("herdr")).isTrue();
  }

  @BeforeEach
  void startHostInTheTempRoot() {
    when(agentRuntime.launchCommand(any(), any())).thenReturn("sleep 30");
    OrchestratorProperties orchestrator =
        OrchestratorProperties.defaults().withRoot(root.toString());
    HerdrApi api =
        new HerdrApi(
            new SystemHost(), new HerdrProperties("herdr", ""), processes, new UnixHerdrWire());
    host =
        new HerdrSessionHost(
            api,
            new HerdrReads(api, mock(Clock.class)),
            new SessionEnvironment(
                new OrchestratorPaths(orchestrator, new SystemHost()),
                new NoWorktreeContainers(),
                new SystemHost(),
                mock(StateService.class)),
            mock(TerminalDriver.class),
            agentRuntime);
  }

  @AfterEach
  void stopAndDeleteTheThrowawaySession() {
    processes.run(null, TEN_SECONDS, List.of("herdr", "--session", SESSION, "server", "stop"));
    processes.run(null, TEN_SECONDS, List.of("herdr", "session", "delete", SESSION));
  }

  @Test
  void leavesOneWindowPerTaskWhenTheSameTaskIsStartedTwice() {
    host.openTaskWindow(SESSION, "jagt", "ABC-1", null, root, LaunchMode.WORK, "");
    host.openTaskWindow(SESSION, "jagt", "ABC-1", null, root, LaunchMode.WORK, "");

    assertThat(host.killTaskWindows(SESSION, "ABC-1")).isEqualTo(1);
  }

  @Test
  void readsTheAgentRunningAndThenTheWindowGoneOnceItIsKilled() {
    host.openTaskWindow(SESSION, "jagt", "ABC-1", null, root, LaunchMode.WORK, "");

    await()
        .atMost(TEN_SECONDS)
        .untilAsserted(() ->
            assertThat(host.taskWindowState(SESSION, "ABC-1"))
                .isEqualTo(SessionHost.WindowState.AGENT_RUNNING));
    host.killTaskWindows(SESSION, "ABC-1");

    assertThat(host.taskWindowState(SESSION, "ABC-1")).isEqualTo(SessionHost.WindowState.MISSING);
  }
}
