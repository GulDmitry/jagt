package dev.jagt.orchestrator.service.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioWindowsTest {

  private final SessionHost hosts = mock(SessionHost.class);
  private final ConfigService config = mock(ConfigService.class);
  private final AgentRuntime agent = mock(AgentRuntime.class);
  private final ScenarioWindows windows = new ScenarioWindows(hosts, config, agent);
  private final CheckerReader.Workspace workspace =
      new CheckerReader.Workspace(Path.of("/ws"), List.of());

  @BeforeEach
  void configAndHostAreReady() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(hosts.sessionName(any())).thenReturn("jagt");
    when(agent.workspaceCommand(Path.of("/ws"), "", "go", workspace)).thenReturn("line");
  }

  @Test
  void opensTheSessionInTheWorkspaceNamedForItsProject() {
    when(agent.workspaceCommand(Path.of("/ws"), "sonnet", "go", workspace)).thenReturn("line");

    windows.open("alpha", "sonnet", "go", workspace);

    verify(hosts).openShellWindow("jagt", "jagt", "acceptance-alpha", Path.of("/ws"), "line");
  }

  @Test
  void focusesTheRunningSessionAndOpensNoSecond() {
    when(hosts.taskWindowState("jagt", "acceptance-alpha"))
        .thenReturn(SessionHost.WindowState.AGENT_RUNNING);
    when(hosts.focusTaskWindow("jagt", "jagt", "acceptance-alpha")).thenReturn(true);

    String said = windows.open("alpha", "", "go", workspace);

    assertThat(said).contains("already runs");
    verify(hosts, never())
        .openShellWindow(anyString(), anyString(), anyString(), any(), anyString());
  }

  @Test
  void opensAgainWhereOnlyTheShellOfAnEndedSessionIsLeft() {
    when(hosts.taskWindowState("jagt", "acceptance-alpha"))
        .thenReturn(SessionHost.WindowState.DEAD_SHELL);

    windows.open("alpha", "", "go", workspace);

    verify(hosts).openShellWindow("jagt", "jagt", "acceptance-alpha", Path.of("/ws"), "line");
  }

  @Test
  void logsGoneOnceWhereTheWindowClosesAfterRunning() {
    windows.open("p0", "", "go", workspace);
    when(hosts.taskWindowState("jagt", "acceptance-p0"))
        .thenReturn(
            SessionHost.WindowState.AGENT_RUNNING,
            SessionHost.WindowState.MISSING,
            SessionHost.WindowState.MISSING);

    try (FakeLogCapture log = new FakeLogCapture(ScenarioWindows.class)) {
      windows.watch();
      windows.watch();
      windows.watch();

      assertThat(log.events())
          .filteredOn(e -> e.getMessage().equals("acceptance session ended"))
          .singleElement()
          .satisfies(e ->
              assertThat(e.getKeyValuePairs())
                  .anySatisfy(kv -> assertThat(kv.key + "=" + kv.value).isEqualTo("ending=gone")));
    }
  }

  @Test
  void logsFinishedWithWhatTheAgentPrintedWhereItLeavesItsShellAfterRunning() {
    windows.open("p1", "", "go", workspace);
    when(hosts.exitTail("jagt", "acceptance-p1")).thenReturn(Optional.of("Goodbye"));
    when(hosts.taskWindowState("jagt", "acceptance-p1"))
        .thenReturn(SessionHost.WindowState.AGENT_RUNNING, SessionHost.WindowState.DEAD_SHELL);

    try (FakeLogCapture log = new FakeLogCapture(ScenarioWindows.class)) {
      windows.watch();
      windows.watch();

      assertThat(log.events())
          .filteredOn(e -> e.getMessage().equals("acceptance session ended"))
          .singleElement()
          .satisfies(e ->
              assertThat(e.getKeyValuePairs())
                  .anySatisfy(
                      kv -> assertThat(kv.key + "=" + kv.value).isEqualTo("ending=finished"))
                  .anySatisfy(
                      kv -> assertThat(kv.key + "=" + kv.value).isEqualTo("cause=Goodbye")));
    }
  }

  @Test
  void namesNoCauseWhereTheAgentPrintedNothingOnItsWayOut() {
    windows.open("p2", "", "go", workspace);
    when(hosts.exitTail("jagt", "acceptance-p2")).thenReturn(Optional.of("  "));
    when(hosts.taskWindowState("jagt", "acceptance-p2"))
        .thenReturn(SessionHost.WindowState.AGENT_RUNNING, SessionHost.WindowState.DEAD_SHELL);

    try (FakeLogCapture log = new FakeLogCapture(ScenarioWindows.class)) {
      windows.watch();
      windows.watch();

      assertThat(log.events())
          .filteredOn(e -> e.getMessage().equals("acceptance session ended"))
          .hasSize(1)
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .noneMatch(kv -> kv.key.equals("cause"));
    }
  }

  @Test
  void logsFailedWhereTheAgentLeavesItsShellWithoutEverRunning() {
    windows.open("p3", "", "go", workspace);
    when(hosts.taskWindowState("jagt", "acceptance-p3"))
        .thenReturn(SessionHost.WindowState.DEAD_SHELL);

    try (FakeLogCapture log = new FakeLogCapture(ScenarioWindows.class)) {
      windows.watch();

      assertThat(log.events())
          .filteredOn(e -> e.getMessage().equals("acceptance session ended"))
          .singleElement()
          .satisfies(e ->
              assertThat(e.getKeyValuePairs())
                  .anySatisfy(
                      kv -> assertThat(kv.key + "=" + kv.value).isEqualTo("ending=failed")));
    }
  }

  @Test
  void logsNothingWhereTheWindowIsNotYetThereToRead() {
    windows.open("p4", "", "go", workspace);
    when(hosts.taskWindowState("jagt", "acceptance-p4"))
        .thenReturn(SessionHost.WindowState.MISSING);

    try (FakeLogCapture log = new FakeLogCapture(ScenarioWindows.class)) {
      windows.watch();

      assertThat(log.events())
          .filteredOn(e -> e.getMessage().equals("acceptance session ended"))
          .isEmpty();
    }
  }
}
