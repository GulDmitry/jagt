package dev.jagt.orchestrator.adapter.tmux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.SessionHost.WindowState;
import dev.jagt.orchestrator.port.TerminalDriver;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

class TmuxSessionHostTest {

  @ParameterizedTest
  @ValueSource(strings = {"jagt:agents", "jagt.agents"})
  void refusesASessionNameTmuxWouldReadAsAWindowOrAPane(String name) {
    assertThat(TmuxSessionHost.reserved(name)).contains("contains ':' or '.', which tmux reserves");
  }

  @Test
  void throwsRatherThanReportingNoWindowWhenTheWindowIsThereButItsScreenCannotBeRead() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("capture-pane"))))
        .thenReturn(new Processes.Result(1, "", "can't find pane"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThatThrownBy(() -> host.screenOf("jagt", "ABC-1"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void listsWindowsOnceAndReadsTheProcessTableOnceForEveryTaskOfATick() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat(
                (List<String> command) ->
                    command.contains("list-windows") && command.contains("-a"))))
        .thenReturn(
            new Processes.Result(
                0,
                "jagt\t@1 100 1700000000 ABC-1\n"
                    + "jagt\t@2 200 1700000001 ABC-2\n"
                    + "jagt\t@3 300 1700000002 ABC-3\n",
                ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("ps"))))
        .thenReturn(new Processes.Result(0, "    1\n  100\n  300\n", ""));
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            reads,
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));
    List<WindowState> states = new java.util.ArrayList<>();

    reads.once(
        () -> {
          states.add(host.taskWindowState("jagt", "ABC-1"));
          states.add(host.taskWindowState("jagt", "ABC-2"));
          states.add(host.taskWindowState("jagt", "ABC-3"));
        });

    assertThat(states)
        .containsExactly(
            WindowState.AGENT_RUNNING, WindowState.DEAD_SHELL, WindowState.AGENT_RUNNING);
    verify(processes, times(1))
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows")));
    verify(processes, times(1))
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("ps")));
    verify(processes, never())
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("pgrep")));
  }

  @Test
  void listsWindowsAgainWhenATaskWindowIsPressedWithinTheTick() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("send-keys"))))
        .thenReturn(new Processes.Result(0, "", ""));
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            reads,
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    reads.once(
        () -> {
          host.lastWindowActivityMillis("jagt", "ABC-1");
          host.submitTaskWindow("jagt", "ABC-1");
          host.lastWindowActivityMillis("jagt", "ABC-1");
        });

    verify(processes, times(2))
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows")));
  }

  @Test
  void listsWindowsAgainWhenATaskWindowIsOpenedWithinTheTick() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""));
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            reads,
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    reads.once(
        () -> {
          host.lastWindowActivityMillis("jagt", "ABC-1");
          host.keepFeedWindow("jagt", "feed", Path.of("/feeds/a.log"));
          host.lastWindowActivityMillis("jagt", "ABC-1");
        });

    verify(processes, times(2))
        .run(
            isNull(),
            any(Duration.class),
            argThat(
                (List<String> command) ->
                    command.contains("list-windows") && command.contains("-a")));
  }

  @Test
  void listsWindowsAgainWhenATaskWindowIsKilledWithinTheTick() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""));
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            reads,
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    reads.once(
        () -> {
          host.lastWindowActivityMillis("jagt", "ABC-1");
          host.killTaskWindows("jagt", "ABC-1");
          host.lastWindowActivityMillis("jagt", "ABC-1");
        });

    verify(processes, times(2))
        .run(
            isNull(),
            any(Duration.class),
            argThat(
                (List<String> command) ->
                    command.contains("list-windows") && command.contains("-a")));
  }

  @Test
  void readsOnlyTheWindowsOfTheSessionAskedAboutWithinATick() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "other\t@4 400 1700000009 ABC-4\n", ""));
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            reads,
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));
    List<Long> activity = new java.util.ArrayList<>();

    reads.once(() -> activity.add(host.lastWindowActivityMillis("jagt", "ABC-4")));

    assertThat(activity).containsExactly(0L);
  }

  @Test
  void listsWindowsAgainForAReadAfterTheTickEnded() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""));
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            reads,
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    reads.once(() -> host.lastWindowActivityMillis("jagt", "ABC-1"));
    host.lastWindowActivityMillis("jagt", "ABC-1");

    verify(processes, times(2))
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows")));
  }

  @Test
  void acceptsASessionNameCarryingNeitherReservedCharacter() {
    assertThat(TmuxSessionHost.reserved("agents")).isEmpty();
  }

  @Test
  void namesTheSessionJagtWhereNoneIsConfigured() {
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            mock(Processes.class),
            new TmuxReads(mock(Processes.class), properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.sessionName(null)).isEqualTo("jagt");
    assertThat(host.sessionName("  ")).isEqualTo("jagt");
    assertThat(host.sessionName("work")).isEqualTo("work");
  }

  @Test
  void refusesThroughTheHostASessionNameTmuxReserves() {
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            mock(Processes.class),
            new TmuxReads(mock(Processes.class), properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.unaddressable("a:b")).isPresent();
    assertThat(host.unaddressable("ab")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"0, 4242, AGENT_RUNNING", "0, '', DEAD_SHELL", "1, '', DEAD_SHELL"})
  void tellsAWindowWhoseAgentRunsFromOneLeftWithItsShell(
      int pgrepExit, String children, WindowState expected) {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("pgrep"))))
        .thenReturn(new Processes.Result(pgrepExit, children, ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(expected);
  }

  @Test
  void reportsAMissingWindowWhereTheSessionListsNone() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.MISSING);
    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isZero();
  }

  @Test
  void readsTheLastOutputOfTheTasksOwnWindowNotOfOneWhoseNameEndsTheSame() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(
            new Processes.Result(0, "@1 100 1700000000 XABC-1\n@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isEqualTo(1_700_000_500_000L);
  }

  @Test
  void killsEveryWindowOfTheTaskAndCountsTheOnesThatWentAway() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@1 XABC-1\n@2 ABC-1\n@3 ABC-1\n@4 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat(
                (List<String> command) ->
                    command.contains("kill-window") && command.contains("@2"))))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat(
                (List<String> command) ->
                    command.contains("kill-window") && command.contains("@3"))))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat(
                (List<String> command) ->
                    command.contains("kill-window") && command.contains("@4"))))
        .thenReturn(new Processes.Result(1, "", "gone"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    int killed = host.killTaskWindows("jagt", "ABC-1");

    assertThat(killed).isEqualTo(2);
    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues()).noneMatch(command -> command.contains("@1"));
  }

  @Test
  void killsNothingWhereTheSessionListsNoWindow() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.killTaskWindows("jagt", "ABC-1")).isZero();
  }

  @Test
  void refusesToFocusAWindowTheTaskDoesNotHave() {
    Processes processes = mock(Processes.class);
    TerminalDriver terminal = mock(TerminalDriver.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            terminal,
            mock(AgentRuntime.class));

    assertThat(host.focusTaskWindow("jagt", "agents", "ABC-1")).isFalse();
    verify(terminal, never()).openViewer(any(), any(), any());
  }

  @Test
  void selectsTheTasksWindowAndOpensTheViewerWhereNoClientIsAttached() {
    Processes processes = mock(Processes.class);
    TerminalDriver terminal = mock(TerminalDriver.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(
            new Processes.Result(0, "@1 100 1700000000 XABC-1\n@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            terminal,
            mock(AgentRuntime.class));

    boolean focused = host.focusTaskWindow("jagt", "agents", "ABC-1");

    assertThat(focused).isTrue();
    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command ->
                assertThat(command).startsWith("/opt/jagt/tmux", "select-window", "-t", "@2"));
    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("agents"), any());
  }

  @Test
  void everyTmuxCommandNamesTheServerWhereASocketIsConfigured() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties =
        ToolchainProperties.defaults()
            .withTmuxCommand("/opt/jagt/tmux")
            .withTmuxSocket("jagt-sock");
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.focusTaskWindow("jagt", "agents", "ABC-1");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .isNotEmpty()
        .allSatisfy(command -> assertThat(command).startsWith("/opt/jagt/tmux", "-L", "jagt-sock"));
  }

  @Test
  void theViewerAttachesToTheSessionOnTheConfiguredSocket() {
    Processes processes = mock(Processes.class);
    TerminalDriver terminal = mock(TerminalDriver.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties =
        ToolchainProperties.defaults()
            .withTmuxCommand("/opt/jagt/tmux")
            .withTmuxSocket("jagt-sock");
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            terminal,
            mock(AgentRuntime.class));

    host.focusTaskWindow("jagt", "agents", "ABC-1");

    verify(terminal)
        .openViewer(
            eq(List.of("/opt/jagt/tmux", "-L", "jagt-sock", "attach", "-t", "jagt")),
            eq("agents"),
            any());
  }

  @Test
  void scopesTheWindowBarAndTitleOptionsToTheTasksSessionAlone() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    ToolchainProperties properties = ToolchainProperties.defaults().withTmuxCommand("tmux");
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.keepFeedWindow("jagt", "feed", Path.of("/feed.log"));

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .filteredOn(command -> command.contains("set-titles"))
        .singleElement()
        .satisfies(
            options ->
                assertThat(options)
                    .containsSequence("set-option", "-t", "jagt", "set-titles", "on")
                    .containsSequence(
                        "set-option", "-t", "jagt", "status-style", "bg=colour252,fg=colour235")
                    .containsSequence("set-hook", "-t", "jagt", "after-new-window")
                    .doesNotContainSequence("-g", "set-titles")
                    .doesNotContain("window-status-format"));
  }

  @Test
  void opensNoSecondViewerWhereAClientIsAlreadyAttached() {
    Processes processes = mock(Processes.class);
    TerminalDriver terminal = mock(TerminalDriver.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-clients"))))
        .thenReturn(new Processes.Result(0, "/dev/ttys001: jagt", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            terminal,
            mock(AgentRuntime.class));

    host.focusTaskWindow("jagt", "agents", "ABC-1");

    verify(terminal, never()).openViewer(any(), any(), any());
  }

  @Test
  void typesTheNudgeLiterallyThenPressesEnterInTheTasksWindow() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    boolean nudged = host.nudgeTaskWindow("jagt", "ABC-1", "carry on");

    assertThat(nudged).isTrue();
    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command ->
                assertThat(command).containsSequence("send-keys", "-t", "@2", "-l", "carry on"));
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> assertThat(command).containsSequence("send-keys", "-t", "@2", "Enter"));
  }

  @Test
  void nudgesNoWindowTheTaskDoesNotHave() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.nudgeTaskWindow("jagt", "ABC-1", "carry on")).isFalse();
  }

  @Test
  void submitsWhatTheInputHoldsByPressingEnterInTheTasksWindow() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    boolean submitted = host.submitTaskWindow("jagt", "ABC-1");

    assertThat(submitted).isTrue();
    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> assertThat(command).containsSequence("send-keys", "-t", "@2", "Enter"));
  }

  @Test
  void returnsTheScreenOfTheTasksWindow() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("capture-pane"))))
        .thenReturn(new Processes.Result(0, "$ build ok", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.screenOf("jagt", "ABC-1")).contains("$ build ok");
  }

  @Test
  void readsTheLastLinesAnAgentPrintedBeforeItExited() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("pgrep"))))
        .thenReturn(new Processes.Result(1, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("capture-pane"))))
        .thenReturn(
            new Processes.Result(
                0,
                """
                Welcome
                Loading servers
                Error: Invalid MCP configuration:
                  mcpServers.jagt: missing command

                [jagt] agent exited — window closes in 15s (Ctrl+C to close now)


                """,
                ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.exitTail("jagt", "ABC-1"))
        .contains(
            "Loading servers\n"
                + "Error: Invalid MCP configuration:\n"
                + "  mcpServers.jagt: missing command");
  }

  @Test
  void readsNoExitWhileTheAgentRuns() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("pgrep"))))
        .thenReturn(new Processes.Result(0, "4242\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.exitTail("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void reportsNoScreenForATaskWithoutAWindow() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.screenOf("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void launchesTheAgentWithoutAModelFlagWhereNoModelIsAsked() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(agent.launchCommand(Path.of("/wt/ABC-1"), false)).thenReturn("agent-cmd");
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> {
              assertThat(command).containsSequence("new-window", "-P");
              assertThat(command.getLast()).contains("agent-cmd").endsWith("; exec sleep 15");
            });
  }

  @Test
  void restoresWhatTheAgentWasProvisionedWithBeforeOpeningItsWindow() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    InOrder order = inOrder(agent, processes);
    order.verify(agent).beforeLaunch(Path.of("/wt/ABC-1"));
    order
        .verify(processes)
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("new-window")));
  }

  @Test
  void launchesTheAgentOnTheModelAskedFor() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(agent.launchCommand(Path.of("/wt/ABC-1"), true, "opus")).thenReturn("agent-on-opus");
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), true, "opus");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(command -> assertThat(command.getLast()).contains("agent-on-opus"));
  }

  @Test
  void carriesTheAliasInAWindowOptionAndLeavesTheWindowNameTheTask() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", "login fix", Path.of("/wt/ABC-1"), false, "");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command ->
                assertThat(command)
                    .containsSequence("set-option", "-w", "-t", "@9", "@jagt_alias", "login fix"));
    assertThat(commands.getAllValues())
        .anySatisfy(command -> assertThat(command).containsSequence("-n", "ABC-1"));
  }

  @Test
  void setsNoAliasOptionOnAWindowWhoseTaskHasNone() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", "  ", Path.of("/wt/ABC-1"), false, "");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues()).noneMatch(command -> command.contains("@jagt_alias"));
  }

  @Test
  void createsTheSessionWhereNoneExistsYet() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("has-session"))))
        .thenReturn(new Processes.Result(1, "", "no session"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
  }

  @Test
  void createsNoSessionWhereOneExists() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues()).noneMatch(command -> command.contains("new-session"));
  }

  @Test
  void killsTheTasksEarlierWindowBeforeOpeningItsNewOne() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@4 ABC-1\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(command -> assertThat(command).containsSequence("kill-window", "-t", "@4"));
  }

  @Test
  void opensTheViewerWhereATaskWindowIsOpenedAndNoClientIsAttached() {
    Processes processes = mock(Processes.class);
    TerminalDriver terminal = mock(TerminalDriver.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-clients"))))
        .thenReturn(new Processes.Result(0, "", ""));
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            terminal,
            mock(AgentRuntime.class));

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("agents"), any());
  }

  @Test
  void revivesTheTaskInAWindowRunningTheRuntimesReviveCommand() {
    Processes processes = mock(Processes.class);
    AgentRuntime agent = mock(AgentRuntime.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(agent.reviveCommand(Path.of("/wt/ABC-1"))).thenReturn("agent --continue");
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            agent);

    host.reviveTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"));

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> {
              assertThat(command).containsSequence("new-window", "-P");
              assertThat(command.getLast()).contains("agent --continue");
            });
  }

  @Test
  void createsTheSessionForTheFeedWhereNoneExistsYet() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("has-session"))))
        .thenReturn(new Processes.Result(1, "", "no session"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/master.log"));

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
  }

  @Test
  void tailsTheFeedInAWindowOfItsOwnWhereNoneIsOpen() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/it's.log"));

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command ->
                assertThat(command.getLast()).startsWith("tail -n 200 -F '/feeds/it'\\''s.log'"));
  }

  @Test
  void runsACommandAsTheProcessOfAWindowOfItsOwnQuotedWordByWord() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.openCommandWindow("jagt", "jagt", "login", List.of("no-such-cli", "auth", "it's"));

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> {
              assertThat(command).containsSequence("new-window", "-P");
              assertThat(command.getLast()).startsWith("'no-such-cli' 'auth' 'it'\\''s';");
            });
  }

  @Test
  void runsAShellLineInTheFolderGivenAsTheProcessOfAWindowOfItsOwn() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.openShellWindow("jagt", "jagt", "acceptance-alpha", Path.of("/ws"), "agent 'p'");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> {
              assertThat(command).containsSequence("new-window", "-P");
              assertThat(command).containsSequence("-n", "acceptance-alpha", "-c", "/ws");
              assertThat(command.getLast()).startsWith("agent 'p';");
            });
  }

  @Test
  void createsTheSessionAndShowsItToTheHumanForACommandWindow() {
    Processes processes = mock(Processes.class);
    TerminalDriver terminal = mock(TerminalDriver.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("has-session"))))
        .thenReturn(new Processes.Result(1, "", "no session"));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-clients"))))
        .thenReturn(new Processes.Result(0, "", ""));
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            terminal,
            mock(AgentRuntime.class));

    host.openCommandWindow("jagt", "jagt-title", "login", List.of("no-such-cli"));

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command -> assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("jagt-title"), any());
  }

  @Test
  void opensNoSecondFeedWindowWhereOneIsOpen() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@5 100 1700000000 master-feed\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/master.log"));

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues()).noneMatch(command -> command.contains("new-window"));
  }

  @Test
  void submitsNothingWhereTheTaskHasNoWindow() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.submitTaskWindow("jagt", "ABC-1")).isFalse();
  }

  @Test
  void readsAnEmptyTailWhereTheAgentExitedPrintingNothing() {
    Processes processes = mock(Processes.class);
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("pgrep"))))
        .thenReturn(new Processes.Result(1, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("capture-pane"))))
        .thenReturn(
            new Processes.Result(
                0, "[jagt] agent exited \u2014 window closes in 15s (Ctrl+C to close now)\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThat(host.exitTail("jagt", "ABC-1")).contains("");
  }

  @Test
  @ResourceLock(Resources.GLOBAL)
  void warnsWhereTheTaskWindowKeepsItsAutomaticName() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("automatic-rename"))))
        .thenReturn(new Processes.Result(1, "", "no such window"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    Logger hostLog = (Logger) LoggerFactory.getLogger(TmuxSessionHost.class);
    hostLog.addAppender(log);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    assertThat(List.copyOf(log.list))
        .filteredOn(event -> "tmux window rename failed".equals(event.getMessage()))
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.WARN);
    hostLog.detachAppender(log);
  }

  @Test
  @ResourceLock(Resources.GLOBAL)
  void warnsWhereTheSessionOptionsWereNotApplied() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("escape-time"))))
        .thenReturn(new Processes.Result(1, "", "unknown option"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    Logger hostLog = (Logger) LoggerFactory.getLogger(TmuxSessionHost.class);
    hostLog.addAppender(log);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    assertThat(List.copyOf(log.list))
        .filteredOn(event -> "tmux session options failed".equals(event.getMessage()))
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.WARN);
    hostLog.detachAppender(log);
  }

  @Test
  @ResourceLock(Resources.GLOBAL)
  void opensNoFeedWindowAndNamesTheCauseWhereTheSessionsWindowsCannotBeListed() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(1, "", "protocol version mismatch"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));
    ListAppender<ILoggingEvent> log = new ListAppender<>();
    log.start();
    Logger hostLog = (Logger) LoggerFactory.getLogger(TmuxReads.class);
    hostLog.addAppender(log);

    host.keepFeedWindow("jagt", "master", Path.of("/logs/master.log"));

    verify(processes, never())
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("new-window")));
    assertThat(List.copyOf(log.list))
        .filteredOn(event -> "tmux windows unreadable".equals(event.getMessage()))
        .flatExtracting(ILoggingEvent::getKeyValuePairs)
        .anySatisfy(pair -> assertThat(pair.value).isEqualTo("protocol version mismatch"));
    hostLog.detachAppender(log);
  }

  @Test
  void opensNoSecondFeedWindowWhereOneByThatNameExists() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(
            new Processes.Result(0, "@1 100 1700000000 master\n@2 200 1700000500 xmaster\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.keepFeedWindow("jagt", "master", Path.of("/logs/master.log"));

    verify(processes, never())
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("new-window")));
  }

  @Test
  void opensOneFeedWindowWhereNoneByThatNameExists() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(0, "@2 200 1700000500 xmaster\n", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.keepFeedWindow("jagt", "master", Path.of("/logs/master.log"));

    verify(processes)
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("new-window")));
  }

  @Test
  void opensNoTaskWindowWhereTheSessionsWindowsCannotBeListed() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "", ""));
    when(processes.run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("list-windows"))))
        .thenReturn(new Processes.Result(1, "", "server timed out"));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    assertThatThrownBy(
            () ->
                host.openTaskWindow(
                    "jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, ""))
        .isInstanceOf(IllegalStateException.class);
    verify(processes, never())
        .run(
            isNull(),
            any(Duration.class),
            argThat((List<String> command) -> command.contains("new-window")));
  }

  @Test
  void stopsGitClimbingOutOfAScratchFolderWhereTheSessionRuns() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    OrchestratorPaths paths = new OrchestratorPaths(OrchestratorProperties.defaults());
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            paths,
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.openTaskWindow(
        "jagt", "agents", "ABC-1", null, paths.scratch().resolve("ABC-1"), true, "");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .anySatisfy(
            command ->
                assertThat(command.getLast())
                    .startsWith("GIT_CEILING_DIRECTORIES='" + paths.scratch() + "' "));
  }

  @Test
  void leavesGitsCeilingAloneForAWorktreeOfARepository() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), anyList()))
        .thenReturn(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new OrchestratorPaths(OrchestratorProperties.defaults()),
            mock(TerminalDriver.class),
            mock(AgentRuntime.class));

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
    verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
    assertThat(commands.getAllValues())
        .noneSatisfy(command -> assertThat(command.getLast()).contains("GIT_CEILING_DIRECTORIES"));
  }
}
