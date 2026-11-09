package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.CheckerReader;
import dev.jagt.orchestrator.port.SessionHost;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
