package dev.jagt.orchestrator.adapter.tmux;

import static dev.jagt.orchestrator.adapter.tmux.TmuxFixtures.answer;
import static dev.jagt.orchestrator.adapter.tmux.TmuxFixtures.attached;
import static dev.jagt.orchestrator.adapter.tmux.TmuxFixtures.containing;
import static dev.jagt.orchestrator.adapter.tmux.TmuxFixtures.everyCommandAnswering;
import static dev.jagt.orchestrator.adapter.tmux.TmuxFixtures.host;
import static dev.jagt.orchestrator.adapter.tmux.TmuxFixtures.issued;
import static dev.jagt.orchestrator.adapter.tmux.TmuxFixtures.ok;
import static dev.jagt.orchestrator.adapter.tmux.TmuxFixtures.reads;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import dev.jagt.orchestrator.port.SessionHost;
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
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

class TmuxSessionHostTest {

  @ParameterizedTest
  @ValueSource(strings = {"jagt:agents", "jagt.agents"})
  void refusesSessionNameTmuxWouldReadAsWindowOrPane(String name) {
    assertThat(TmuxSessionHost.reserved(name)).contains("contains ':' or '.', which tmux reserves");
  }

  @Test
  void throwsRatherThanReportingNoWindowWhenTheWindowIsThereButItsScreenCannotBeRead() {
    Processes processes = mock(Processes.class);
    answer(processes, "list-windows", new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    answer(processes, "capture-pane", new Processes.Result(1, "", "can't find pane"));
    TmuxSessionHost host = host(processes);

    assertThatThrownBy(() -> host.screenOf("jagt", "ABC-1"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void listsWindowsOnceAndReadsTheProcessTableOnceForEveryTaskOfTick() {
    Processes processes = mock(Processes.class);
    when(processes.run(isNull(), any(Duration.class), containing("list-windows", "-a")))
        .thenReturn(
            new Processes.Result(
                0,
                """
                jagt\t@1 100 1700000000 ABC-1
                jagt\t@2 200 1700000001 ABC-2
                jagt\t@3 300 1700000002 ABC-3
                """,
                ""));
    answer(processes, "ps", new Processes.Result(0, "    1\n  100\n  300\n", ""));
    TmuxReads reads = reads(processes);
    TmuxSessionHost host = host(processes, reads);
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
    verify(processes, times(1)).run(isNull(), any(Duration.class), containing("list-windows"));
    verify(processes, times(1)).run(isNull(), any(Duration.class), containing("ps"));
    verify(processes, never()).run(isNull(), any(Duration.class), containing("pgrep"));
  }

  @Test
  void listsWindowsAgainWhenTaskWindowIsPressedWithinTheTick() {
    Processes processes = mock(Processes.class);
    answer(
        processes, "list-windows", new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""));
    answer(processes, "send-keys", ok(""));
    TmuxReads reads = reads(processes);
    TmuxSessionHost host = host(processes, reads);

    reads.once(
        () -> {
          host.lastWindowActivityMillis("jagt", "ABC-1");
          host.submitTaskWindow("jagt", "ABC-1");
          host.lastWindowActivityMillis("jagt", "ABC-1");
        });

    verify(processes, times(2)).run(isNull(), any(Duration.class), containing("list-windows"));
  }

  @Test
  void listsWindowsAgainWhenTaskWindowIsOpenedWithinTheTick() {
    Processes processes = everyCommandAnswering(ok(""));
    answer(
        processes, "list-windows", new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""));
    TmuxReads reads = reads(processes);
    TmuxSessionHost host = host(processes, reads);

    reads.once(
        () -> {
          host.lastWindowActivityMillis("jagt", "ABC-1");
          host.keepFeedWindow("jagt", "feed", Path.of("/feeds/a.log"));
          host.lastWindowActivityMillis("jagt", "ABC-1");
        });

    verify(processes, times(2))
        .run(isNull(), any(Duration.class), containing("list-windows", "-a"));
  }

  @Test
  void listsWindowsAgainWhenTaskWindowIsKilledWithinTheTick() {
    Processes processes = everyCommandAnswering(ok(""));
    answer(
        processes, "list-windows", new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""));
    TmuxReads reads = reads(processes);
    TmuxSessionHost host = host(processes, reads);

    reads.once(
        () -> {
          host.lastWindowActivityMillis("jagt", "ABC-1");
          host.killTaskWindows("jagt", "ABC-1");
          host.lastWindowActivityMillis("jagt", "ABC-1");
        });

    verify(processes, times(2))
        .run(isNull(), any(Duration.class), containing("list-windows", "-a"));
  }

  @Test
  void readsOnlyTheWindowsOfTheSessionAskedAboutWithinTick() {
    Processes processes = mock(Processes.class);
    answer(
        processes, "list-windows", new Processes.Result(0, "other\t@4 400 1700000009 ABC-4\n", ""));
    TmuxReads reads = reads(processes);
    TmuxSessionHost host = host(processes, reads);
    List<Long> activity = new java.util.ArrayList<>();

    reads.once(() -> activity.add(host.lastWindowActivityMillis("jagt", "ABC-4")));

    assertThat(activity).containsExactly(0L);
  }

  @Test
  void listsWindowsAgainForReadAfterTheTickEnded() {
    Processes processes = mock(Processes.class);
    answer(
        processes, "list-windows", new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""));
    TmuxReads reads = reads(processes);
    TmuxSessionHost host = host(processes, reads);

    reads.once(() -> host.lastWindowActivityMillis("jagt", "ABC-1"));
    host.lastWindowActivityMillis("jagt", "ABC-1");

    verify(processes, times(2)).run(isNull(), any(Duration.class), containing("list-windows"));
  }

  @Test
  void acceptsSessionNameCarryingNeitherReservedCharacter() {
    assertThat(TmuxSessionHost.reserved("agents")).isEmpty();
  }

  @Test
  void namesTheSessionJagtWhereNoneIsConfigured() {
    TmuxSessionHost host = host(mock(Processes.class));

    assertThat(host.sessionName(null)).isEqualTo("jagt");
    assertThat(host.sessionName("  ")).isEqualTo("jagt");
    assertThat(host.sessionName("work")).isEqualTo("work");
  }

  @Test
  void refusesThroughTheHostSessionNameTmuxReserves() {
    TmuxSessionHost host = host(mock(Processes.class));

    assertThat(host.unaddressable("a:b")).isPresent();
    assertThat(host.unaddressable("ab")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"0, 4242, AGENT_RUNNING", "0, '', DEAD_SHELL", "1, '', DEAD_SHELL"})
  void tellsWindowWhoseAgentRunsFromOneLeftWithItsShell(
      int pgrepExit, String children, WindowState expected) {
    Processes processes = mock(Processes.class);
    answer(processes, "list-windows", new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    answer(processes, "pgrep", new Processes.Result(pgrepExit, children, ""));
    TmuxSessionHost host = host(processes);

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(expected);
  }

  @Test
  void reportsMissingWindowWhereTheSessionListsNone() {
    Processes processes =
        everyCommandAnswering(
            new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = host(processes);

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.MISSING);
    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isZero();
  }

  @Test
  void readsTheLastOutputOfTheTasksOwnWindowNotOfOneWhoseNameEndsTheSame() {
    Processes processes =
        everyCommandAnswering(
            new Processes.Result(0, "@1 100 1700000000 XABC-1\n@2 200 1700000500 ABC-1\n", ""));
    TmuxSessionHost host = host(processes);

    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isEqualTo(1_700_000_500_000L);
  }

  @Test
  void killsEveryWindowOfTheTaskAndCountsTheOnesThatWentAway() {
    Processes processes = mock(Processes.class);
    answer(
        processes,
        "list-windows",
        new Processes.Result(0, "@1 XABC-1\n@2 ABC-1\n@3 ABC-1\n@4 ABC-1\n", ""));
    when(processes.run(isNull(), any(Duration.class), containing("kill-window", "@2")))
        .thenReturn(ok(""));
    when(processes.run(isNull(), any(Duration.class), containing("kill-window", "@3")))
        .thenReturn(ok(""));
    when(processes.run(isNull(), any(Duration.class), containing("kill-window", "@4")))
        .thenReturn(new Processes.Result(1, "", "gone"));
    TmuxSessionHost host = host(processes);

    int killed = host.killTaskWindows("jagt", "ABC-1");

    assertThat(killed).isEqualTo(2);
    List<List<String>> commands = issued(processes);
    assertThat(commands).noneMatch(command -> command.contains("@1"));
  }

  @Test
  void killsNothingWhereTheSessionListsNoWindow() {
    Processes processes =
        everyCommandAnswering(
            new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = host(processes);

    assertThat(host.killTaskWindows("jagt", "ABC-1")).isZero();
  }

  @Test
  void refusesToFocusWindowTheTaskDoesNotHave() {
    Processes processes =
        everyCommandAnswering(
            new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TerminalDriver terminal = mock(TerminalDriver.class);
    TmuxSessionHost host = host(processes, terminal);

    assertThat(host.focusTaskWindow("jagt", "agents", "ABC-1")).isFalse();
    verify(terminal, never()).openViewer(any(), any(), any());
  }

  @Test
  void selectsTheTasksWindowAndOpensTheViewerWhereNoClientIsAttached() {
    Processes processes = everyCommandAnswering(ok(""));
    TerminalDriver terminal = mock(TerminalDriver.class);
    answer(
        processes,
        "list-windows",
        new Processes.Result(0, "@1 100 1700000000 XABC-1\n@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host = host(processes, terminal, properties);

    boolean focused = host.focusTaskWindow("jagt", "agents", "ABC-1");

    assertThat(focused).isTrue();
    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command ->
                assertThat(command).startsWith("/opt/jagt/tmux", "select-window", "-t", "@2"));
    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("agents"), any());
  }

  @Test
  void everyTmuxCommandNamesTheServerWhereSocketIsConfigured() {
    Processes processes = everyCommandAnswering(ok(""));
    answer(processes, "list-windows", new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties =
        ToolchainProperties.defaults()
            .withTmuxCommand("/opt/jagt/tmux")
            .withTmuxSocket("jagt-sock");
    TmuxSessionHost host = host(processes, mock(TerminalDriver.class), properties);

    host.focusTaskWindow("jagt", "agents", "ABC-1");

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .isNotEmpty()
        .allSatisfy(command -> assertThat(command).startsWith("/opt/jagt/tmux", "-L", "jagt-sock"));
  }

  @Test
  void theViewerAttachesToTheSessionOnTheConfiguredSocket() {
    Processes processes = everyCommandAnswering(ok(""));
    TerminalDriver terminal = mock(TerminalDriver.class);
    answer(processes, "list-windows", new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    ToolchainProperties properties =
        ToolchainProperties.defaults()
            .withTmuxCommand("/opt/jagt/tmux")
            .withTmuxSocket("jagt-sock");
    TmuxSessionHost host = host(processes, terminal, properties);

    host.focusTaskWindow("jagt", "agents", "ABC-1");

    verify(terminal)
        .openViewer(
            eq(List.of("/opt/jagt/tmux", "-L", "jagt-sock", "attach", "-t", "jagt")),
            eq("agents"),
            any());
  }

  @Test
  void scopesTheWindowBarAndTitleOptionsToTheTasksSessionAlone() {
    Processes processes = everyCommandAnswering(ok(""));
    ToolchainProperties properties = ToolchainProperties.defaults().withTmuxCommand("tmux");
    TmuxSessionHost host = host(processes, mock(TerminalDriver.class), properties);

    host.keepFeedWindow("jagt", "feed", Path.of("/feed.log"));

    List<List<String>> commands = issued(processes);
    assertThat(commands)
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
  void opensNoSecondViewerWhereClientIsAlreadyAttached() {
    Processes processes = everyCommandAnswering(ok(""));
    TerminalDriver terminal = mock(TerminalDriver.class);
    answer(processes, "list-windows", new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    answer(processes, "list-clients", new Processes.Result(0, "/dev/ttys001: jagt", ""));
    TmuxSessionHost host = host(processes, terminal);

    host.focusTaskWindow("jagt", "agents", "ABC-1");

    verify(terminal, never()).openViewer(any(), any(), any());
  }

  @Test
  void typesTheNudgeLiterallyThenPressesEnterInTheTasksWindow() {
    Processes processes = everyCommandAnswering(ok(""));
    answer(processes, "list-windows", new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    TmuxSessionHost host = host(processes);

    boolean nudged = host.nudgeTaskWindow("jagt", "ABC-1", "carry on");

    assertThat(nudged).isTrue();
    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command ->
                assertThat(command).containsSequence("send-keys", "-t", "@2", "-l", "carry on"));
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("send-keys", "-t", "@2", "Enter"));
  }

  @Test
  void nudgesNoWindowTheTaskDoesNotHave() {
    Processes processes =
        everyCommandAnswering(
            new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = host(processes);

    assertThat(host.nudgeTaskWindow("jagt", "ABC-1", "carry on")).isFalse();
  }

  @Test
  void submitsWhatTheInputHoldsByPressingEnterInTheTasksWindow() {
    Processes processes = everyCommandAnswering(ok(""));
    answer(processes, "list-windows", new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    TmuxSessionHost host = host(processes);

    boolean submitted = host.submitTaskWindow("jagt", "ABC-1");

    assertThat(submitted).isTrue();
    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("send-keys", "-t", "@2", "Enter"));
  }

  @Test
  void returnsTheScreenOfTheTasksWindow() {
    Processes processes = mock(Processes.class);
    answer(processes, "list-windows", new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    answer(processes, "capture-pane", new Processes.Result(0, "$ build ok", ""));
    TmuxSessionHost host = host(processes);

    assertThat(host.screenOf("jagt", "ABC-1")).contains("$ build ok");
  }

  @Test
  void readsTheLastLinesAnAgentPrintedBeforeItExited() {
    Processes processes = mock(Processes.class);
    answer(processes, "list-windows", new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    answer(processes, "pgrep", new Processes.Result(1, "", ""));
    answer(
        processes,
        "capture-pane",
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
    TmuxSessionHost host = host(processes);

    assertThat(host.exitTail("jagt", "ABC-1"))
        .contains(
            """
            Loading servers
            Error: Invalid MCP configuration:
              mcpServers.jagt: missing command\
            """);
  }

  @Test
  void readsNoExitWhileTheAgentRuns() {
    Processes processes = mock(Processes.class);
    answer(processes, "list-windows", new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    answer(processes, "pgrep", new Processes.Result(0, "4242\n", ""));
    TmuxSessionHost host = host(processes);

    assertThat(host.exitTail("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void reportsNoScreenForTaskWithoutWindow() {
    Processes processes =
        everyCommandAnswering(
            new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = host(processes);

    assertThat(host.screenOf("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void launchesTheAgentWithoutModelFlagWhereNoModelIsAsked() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    when(agent.launchCommand(Path.of("/wt/ABC-1"), false)).thenReturn("agent-cmd");
    TmuxSessionHost host = host(processes, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> {
              assertThat(command).containsSequence("new-window", "-P");
              assertThat(command.getLast()).contains("agent-cmd").endsWith("; exec sleep 15");
            });
  }

  @Test
  void restoresWhatTheAgentWasProvisionedWithBeforeOpeningItsWindow() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    TmuxSessionHost host = host(processes, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    InOrder order = inOrder(agent, processes);
    order.verify(agent).beforeLaunch(Path.of("/wt/ABC-1"));
    order.verify(processes).run(isNull(), any(Duration.class), containing("new-window"));
  }

  @Test
  void launchesTheAgentOnTheModelAskedFor() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    when(agent.launchCommand(Path.of("/wt/ABC-1"), true, "opus")).thenReturn("agent-on-opus");
    TmuxSessionHost host = host(processes, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), true, "opus");

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(command -> assertThat(command.getLast()).contains("agent-on-opus"));
  }

  @Test
  void carriesTheAliasInWindowOptionAndLeavesTheWindowNameTheTask() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    TmuxSessionHost host = host(processes, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", "login fix", Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command ->
                assertThat(command)
                    .containsSequence("set-option", "-w", "-t", "@9", "@jagt_alias", "login fix"));
    assertThat(commands).anySatisfy(command -> assertThat(command).containsSequence("-n", "ABC-1"));
  }

  @Test
  void setsNoAliasOptionOnWindowWhoseTaskHasNone() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    TmuxSessionHost host = host(processes, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", "  ", Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = issued(processes);
    assertThat(commands).noneMatch(command -> command.contains("@jagt_alias"));
  }

  @Test
  void createsTheSessionWhereNoneExistsYet() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    answer(processes, "has-session", new Processes.Result(1, "", "no session"));
    TmuxSessionHost host = host(processes, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
  }

  @Test
  void createsNoSessionWhereOneExists() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    TmuxSessionHost host = host(processes, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = issued(processes);
    assertThat(commands).noneMatch(command -> command.contains("new-session"));
  }

  @Test
  void killsTheTasksEarlierWindowBeforeOpeningItsNewOne() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    answer(processes, "list-windows", new Processes.Result(0, "@4 ABC-1\n", ""));
    TmuxSessionHost host = host(processes, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(command -> assertThat(command).containsSequence("kill-window", "-t", "@4"));
  }

  @Test
  void opensTheViewerWhereTaskWindowIsOpenedAndNoClientIsAttached() {
    Processes processes = everyCommandAnswering(ok("@9"));
    TerminalDriver terminal = mock(TerminalDriver.class);
    answer(processes, "list-clients", ok(""));
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host = host(processes, terminal, properties);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("agents"), any());
  }

  @Test
  void revivesTheTaskInWindowRunningTheRuntimesReviveCommand() {
    Processes processes = everyCommandAnswering(ok("@9"));
    AgentRuntime agent = mock(AgentRuntime.class);
    when(agent.reviveCommand(Path.of("/wt/ABC-1"))).thenReturn("agent --continue");
    TmuxSessionHost host = host(processes, agent);

    host.reviveTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"));

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> {
              assertThat(command).containsSequence("new-window", "-P");
              assertThat(command.getLast()).contains("agent --continue");
            });
  }

  @Test
  void createsTheSessionForTheFeedWhereNoneExistsYet() {
    Processes processes = everyCommandAnswering(ok("@9"));
    answer(processes, "has-session", new Processes.Result(1, "", "no session"));
    TmuxSessionHost host = host(processes);

    host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/master.log"));

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
  }

  @Test
  void tailsTheFeedInWindowOfItsOwnWhereNoneIsOpen() {
    Processes processes = everyCommandAnswering(ok("@9"));
    TmuxSessionHost host = host(processes);

    host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/it's.log"));

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command ->
                assertThat(command.getLast()).startsWith("tail -n 200 -F '/feeds/it'\\''s.log'"));
  }

  @Test
  void runsCommandAsTheProcessOfWindowOfItsOwnQuotedWordByWord() {
    Processes processes = everyCommandAnswering(ok("@9"));
    TmuxSessionHost host = host(processes);

    host.openCommandWindow("jagt", "jagt", "login", List.of("no-such-cli", "auth", "it's"));

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> {
              assertThat(command).containsSequence("new-window", "-P");
              assertThat(command.getLast()).startsWith("'no-such-cli' 'auth' 'it'\\''s';");
            });
  }

  @Test
  void runsShellLineInTheFolderGivenAsTheProcessOfWindowOfItsOwn() {
    Processes processes = everyCommandAnswering(ok("@9"));
    TmuxSessionHost host = host(processes);

    host.openShellWindow("jagt", "jagt", "acceptance-alpha", Path.of("/ws"), "agent 'p'");

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> {
              assertThat(command).containsSequence("new-window", "-P");
              assertThat(command).containsSequence("-n", "acceptance-alpha", "-c", "/ws");
              assertThat(command.getLast()).startsWith("agent 'p';");
            });
  }

  @Test
  void createsTheSessionAndShowsItToTheHumanForCommandWindow() {
    Processes processes = everyCommandAnswering(ok("@9"));
    TerminalDriver terminal = mock(TerminalDriver.class);
    answer(processes, "has-session", new Processes.Result(1, "", "no session"));
    answer(processes, "list-clients", ok(""));
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host = host(processes, terminal, properties);

    host.openCommandWindow("jagt", "jagt-title", "login", List.of("no-such-cli"));

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("jagt-title"), any());
  }

  @Test
  void opensNoSecondFeedWindowWhereOneIsOpen() {
    Processes processes = everyCommandAnswering(ok("@9"));
    answer(
        processes, "list-windows", new Processes.Result(0, "@5 100 1700000000 master-feed\n", ""));
    TmuxSessionHost host = host(processes);

    host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/master.log"));

    List<List<String>> commands = issued(processes);
    assertThat(commands).noneMatch(command -> command.contains("new-window"));
  }

  @Test
  void submitsNothingWhereTheTaskHasNoWindow() {
    Processes processes =
        everyCommandAnswering(
            new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = host(processes);

    assertThat(host.submitTaskWindow("jagt", "ABC-1")).isFalse();
  }

  @Test
  void readsAnEmptyTailWhereTheAgentExitedPrintingNothing() {
    Processes processes = mock(Processes.class);
    answer(processes, "list-windows", new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
    answer(processes, "pgrep", new Processes.Result(1, "", ""));
    answer(
        processes,
        "capture-pane",
        new Processes.Result(
            0, "[jagt] agent exited — window closes in 15s (Ctrl+C to close now)\n", ""));
    TmuxSessionHost host = host(processes);

    assertThat(host.exitTail("jagt", "ABC-1")).contains("");
  }

  @Test
  @ResourceLock(Resources.GLOBAL)
  void warnsWhereTheTaskWindowKeepsItsAutomaticName() {
    Processes processes = everyCommandAnswering(ok("@9"));
    answer(processes, "automatic-rename", new Processes.Result(1, "", "no such window"));
    TmuxSessionHost host = host(processes);
    Logger hostLog = (Logger) LoggerFactory.getLogger(TmuxSessionHost.class);
    ListAppender<ILoggingEvent> log = attached(hostLog);

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
    Processes processes = everyCommandAnswering(ok("@9"));
    answer(processes, "escape-time", new Processes.Result(1, "", "unknown option"));
    TmuxSessionHost host = host(processes);
    Logger hostLog = (Logger) LoggerFactory.getLogger(TmuxSessionHost.class);
    ListAppender<ILoggingEvent> log = attached(hostLog);

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
    Processes processes = everyCommandAnswering(ok(""));
    answer(processes, "list-windows", new Processes.Result(1, "", "protocol version mismatch"));
    TmuxSessionHost host = host(processes);
    Logger hostLog = (Logger) LoggerFactory.getLogger(TmuxReads.class);
    ListAppender<ILoggingEvent> log = attached(hostLog);

    host.keepFeedWindow("jagt", "master", Path.of("/logs/master.log"));

    verify(processes, never()).run(isNull(), any(Duration.class), containing("new-window"));
    assertThat(List.copyOf(log.list))
        .filteredOn(event -> "tmux windows unreadable".equals(event.getMessage()))
        .flatExtracting(ILoggingEvent::getKeyValuePairs)
        .anySatisfy(pair -> assertThat(pair.value).isEqualTo("protocol version mismatch"));
    hostLog.detachAppender(log);
  }

  @Test
  void opensNoSecondFeedWindowWhereOneByThatNameExists() {
    Processes processes = everyCommandAnswering(ok(""));
    answer(
        processes,
        "list-windows",
        new Processes.Result(0, "@1 100 1700000000 master\n@2 200 1700000500 xmaster\n", ""));
    TmuxSessionHost host = host(processes);

    host.keepFeedWindow("jagt", "master", Path.of("/logs/master.log"));

    verify(processes, never()).run(isNull(), any(Duration.class), containing("new-window"));
  }

  @Test
  void opensOneFeedWindowWhereNoneByThatNameExists() {
    Processes processes = everyCommandAnswering(ok("@9"));
    answer(processes, "list-windows", new Processes.Result(0, "@2 200 1700000500 xmaster\n", ""));
    TmuxSessionHost host = host(processes);

    host.keepFeedWindow("jagt", "master", Path.of("/logs/master.log"));

    verify(processes).run(isNull(), any(Duration.class), containing("new-window"));
  }

  @Test
  void opensNoTaskWindowWhereTheSessionsWindowsCannotBeListed() {
    Processes processes = everyCommandAnswering(ok(""));
    answer(processes, "list-windows", new Processes.Result(1, "", "server timed out"));
    TmuxSessionHost host = host(processes);

    assertThatThrownBy(
            () ->
                host.openTaskWindow(
                    "jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, ""))
        .isInstanceOf(IllegalStateException.class);
    verify(processes, never()).run(isNull(), any(Duration.class), containing("new-window"));
  }

  @Test
  void stopsGitClimbingOutOfScratchFolderWhereTheSessionRuns() {
    Processes processes = everyCommandAnswering(ok("@9"));
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

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command ->
                assertThat(command.getLast())
                    .startsWith("GIT_CEILING_DIRECTORIES='" + paths.scratch() + "' "));
  }

  @Test
  void leavesGitsCeilingAloneForWorktreeOfRepository() {
    Processes processes = everyCommandAnswering(ok("@9"));
    TmuxSessionHost host = host(processes);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .noneSatisfy(command -> assertThat(command.getLast()).contains("GIT_CEILING_DIRECTORIES"));
  }

  @ParameterizedTest
  @CsvSource({
    "ESCAPE,Escape",
    "UP,Up",
    "DOWN,Down",
    "LEFT,Left",
    "RIGHT,Right",
    "TAB,Tab",
    "SPACE,Space",
    "ENTER,Enter",
    "CTRL_C,C-c",
    "CTRL_U,C-u",
    "DIGIT_1,1",
    "DIGIT_2,2",
    "DIGIT_3,3",
    "DIGIT_4,4",
    "DIGIT_5,5",
    "DIGIT_6,6",
    "DIGIT_7,7",
    "DIGIT_8,8",
    "DIGIT_9,9"
  })
  void pressesKeyByItsNameInTheTasksWindowNeverAsLiteralText(SessionHost.Key key, String tmuxName) {
    Processes processes = everyCommandAnswering(ok(""));
    answer(processes, "list-windows", new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
    TmuxSessionHost host = host(processes);

    boolean pressed = host.pressTaskWindow("jagt", "ABC-1", key);

    assertThat(pressed).isTrue();
    List<List<String>> commands = issued(processes);
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("send-keys", "-t", "@2", tmuxName))
        .noneSatisfy(command -> assertThat(command).contains("-l"));
  }

  @Test
  void listsWindowsAgainWhenKeyIsPressedWithinTheTick() {
    Processes processes = everyCommandAnswering(ok(""));
    answer(processes, "list-windows", ok("jagt\t@1 100 1700000000 ABC-1\n"));
    TmuxReads reads = reads(processes);
    TmuxSessionHost host = host(processes, reads);

    reads.once(
        () -> {
          host.lastWindowActivityMillis("jagt", "ABC-1");
          host.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.DOWN);
          host.lastWindowActivityMillis("jagt", "ABC-1");
        });

    verify(processes, times(2)).run(isNull(), any(Duration.class), containing("list-windows"));
  }

  @Test
  void pressesNoKeyWhereTheTaskHasNoWindow() {
    Processes processes =
        everyCommandAnswering(
            new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = host(processes);

    assertThat(host.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.ESCAPE)).isFalse();
  }
}
