package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.port.SessionHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ScenarioWindowsTest {

  private final SessionHost hosts = mock(SessionHost.class);
  private final ConfigService config = mock(ConfigService.class);
  private final AgentRuntime agent = mock(AgentRuntime.class);
  private final ScenarioWindows windows = new ScenarioWindows(hosts, config, agent);
  private final CheckerReader.Workspace workspace =
      new CheckerReader.Workspace(Path.of("/ws"), List.of());

  @Test
  void opensTheSessionInTheWorkspaceNamedForItsProject() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(hosts.sessionName(any())).thenReturn("jagt");
    when(agent.workspaceCommand(Path.of("/ws"), "sonnet", "go", workspace)).thenReturn("line");

    windows.open("alpha", "sonnet", "go", workspace);

    verify(hosts).openShellWindow("jagt", "jagt", "acceptance-alpha", Path.of("/ws"), "line");
  }

  @Test
  void focusesTheRunningSessionAndOpensNoSecond() {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(hosts.sessionName(any())).thenReturn("jagt");
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
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(hosts.sessionName(any())).thenReturn("jagt");
    when(hosts.taskWindowState("jagt", "acceptance-alpha"))
        .thenReturn(SessionHost.WindowState.DEAD_SHELL);
    when(agent.workspaceCommand(Path.of("/ws"), "", "go", workspace)).thenReturn("line");

    windows.open("alpha", "", "go", workspace);

    verify(hosts).openShellWindow("jagt", "jagt", "acceptance-alpha", Path.of("/ws"), "line");
  }

  private List<ILoggingEvent> watched(String project, SessionHost.WindowState... states) {
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    Logger windowsLog = (Logger) LoggerFactory.getLogger(ScenarioWindows.class);
    windowsLog.addAppender(log);
    try {
      for (SessionHost.WindowState state : states) {
        when(hosts.taskWindowState("jagt", "acceptance-" + project)).thenReturn(state);
        windows.watch();
      }
    } finally {
      windowsLog.detachAppender(log);
    }
    return log.list.stream()
        .filter(e -> e.getMessage().equals("acceptance session ended"))
        .filter(e -> e.getKeyValuePairs().stream().anyMatch(kv -> project.equals(kv.value)))
        .toList();
  }

  private void opened(String project) {
    when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    when(hosts.sessionName(any())).thenReturn("jagt");
    when(agent.workspaceCommand(Path.of("/ws"), "", "go", workspace)).thenReturn("line");
    windows.open(project, "", "go", workspace);
  }

  @Test
  void logsGoneOnceWhereTheWindowClosesAfterRunning() {
    opened("p0");

    List<ILoggingEvent> ended =
        watched(
            "p0",
            SessionHost.WindowState.AGENT_RUNNING,
            SessionHost.WindowState.MISSING,
            SessionHost.WindowState.MISSING);

    assertThat(ended).singleElement().satisfies(e -> assertThat(e.getKeyValuePairs())
        .anySatisfy(kv -> assertThat(kv.key + "=" + kv.value).isEqualTo("ending=gone")));
  }

  @Test
  void logsFinishedWithWhatTheAgentPrintedWhereItLeavesItsShellAfterRunning() {
    opened("p1");
    when(hosts.exitTail("jagt", "acceptance-p1")).thenReturn(Optional.of("Goodbye"));

    List<ILoggingEvent> ended =
        watched("p1", SessionHost.WindowState.AGENT_RUNNING, SessionHost.WindowState.DEAD_SHELL);

    assertThat(ended).singleElement().satisfies(e -> assertThat(e.getKeyValuePairs())
        .anySatisfy(kv -> assertThat(kv.key + "=" + kv.value).isEqualTo("ending=finished"))
        .anySatisfy(kv -> assertThat(kv.key + "=" + kv.value).isEqualTo("cause=Goodbye")));
  }

  @Test
  void namesNoCauseWhereTheAgentPrintedNothingOnItsWayOut() {
    opened("p2");
    when(hosts.exitTail("jagt", "acceptance-p2")).thenReturn(Optional.of("  "));

    List<ILoggingEvent> ended =
        watched("p2", SessionHost.WindowState.AGENT_RUNNING, SessionHost.WindowState.DEAD_SHELL);

    assertThat(ended)
        .singleElement()
        .satisfies(e -> assertThat(e.getKeyValuePairs()).noneMatch(kv -> kv.key.equals("cause")));
  }

  @Test
  void logsFailedWhereTheAgentLeavesItsShellWithoutEverRunning() {
    opened("p3");

    List<ILoggingEvent> ended = watched("p3", SessionHost.WindowState.DEAD_SHELL);

    assertThat(ended).singleElement().satisfies(e -> assertThat(e.getKeyValuePairs())
        .anySatisfy(kv -> assertThat(kv.key + "=" + kv.value).isEqualTo("ending=failed")));
  }

  @Test
  void logsNothingWhereTheWindowIsNotYetThereToRead() {
    opened("p4");

    assertThat(watched("p4", SessionHost.WindowState.MISSING)).isEmpty();
  }
}
