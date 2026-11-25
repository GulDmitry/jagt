package dev.jagt.orchestrator.adapter.tmux;

import static dev.jagt.orchestrator.adapter.tmux.TestTmuxHostBuilder.tmuxHost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.adapter.worktree.DockerComposeContainers;
import dev.jagt.orchestrator.adapter.worktree.NoWorktreeContainers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.ToolchainProperties;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.port.terminal.SessionHost.WindowState;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import dev.jagt.orchestrator.testing.FakeHost;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TmuxSessionHostTest {

  private final TerminalDriver terminal = mock(TerminalDriver.class);
  private final AgentRuntime agent = mock(AgentRuntime.class);
  private final FakeProcesses processes =
      new FakeProcesses().answering(new Processes.Result(0, "@9", ""));
  private final TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

  @ParameterizedTest
  @ValueSource(strings = {"jagt:agents", "jagt.agents"})
  void refusesSessionNameTmuxWouldReadAsWindowOrPane(String name) {
    assertThat(TmuxSessionHost.reserved(name)).contains("contains ':' or '.', which tmux reserves");
  }

  @Test
  void throwsRatherThanReportingNoWindowWhenTheWindowIsThereButItsScreenCannotBeRead() {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(1, "", "can't find pane"), "capture-pane");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThatThrownBy(() -> host.screenOf("jagt", "ABC-1"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void listsWindowsOnceAndReadsTheProcessTableOnceForEveryTaskOfTick() {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(
        new Processes.Result(
            0,
            """
            jagt\t@1 100 1700000000 ABC-1
            jagt\t@2 200 1700000001 ABC-2
            jagt\t@3 300 1700000002 ABC-3
            """,
            ""),
        "list-windows",
        "-a");
    processes.answering(new Processes.Result(0, "    1\n  100\n  300\n", ""), "ps");
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host = tmuxHost().build(processes, reads, terminal, agent);
    List<WindowState> states = new java.util.ArrayList<>();

    reads.once(() -> {
      states.add(host.taskWindowState("jagt", "ABC-1"));
      states.add(host.taskWindowState("jagt", "ABC-2"));
      states.add(host.taskWindowState("jagt", "ABC-3"));
    });

    assertThat(states)
        .containsExactly(
            WindowState.AGENT_RUNNING, WindowState.DEAD_SHELL, WindowState.AGENT_RUNNING);
    assertThat(processes.issuedContaining("list-windows")).hasSize(1);
    assertThat(processes.issuedContaining("ps")).hasSize(1);
    assertThat(processes.issuedContaining("pgrep")).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("windowChanges")
  void listsWindowsAgainWhenWindowIsChangedWithinTheTick(Consumer<TmuxSessionHost> change) {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    processes.answering(
        new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""), "list-windows");
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host = tmuxHost().build(processes, reads, terminal, agent);

    reads.once(() -> {
      host.lastWindowActivityMillis("jagt", "ABC-1");
      change.accept(host);
      host.lastWindowActivityMillis("jagt", "ABC-1");
    });

    assertThat(processes.issuedContaining("list-windows", "-a")).hasSize(2);
  }

  private static Stream<Consumer<TmuxSessionHost>> windowChanges() {
    return Stream.of(
        host -> host.submitTaskWindow("jagt", "ABC-1"),
        host -> host.openCommandWindow("jagt", "jagt", "login", List.of("no-such-cli")),
        host -> host.killTaskWindows("jagt", "ABC-1"),
        host -> host.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.DOWN));
  }

  @Test
  void readsOnlyTheWindowsOfTheSessionAskedAboutWithinTick() {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(
        new Processes.Result(0, "other\t@4 400 1700000009 ABC-4\n", ""), "list-windows");
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host = tmuxHost().build(processes, reads, terminal, agent);
    List<Long> activity = new java.util.ArrayList<>();

    reads.once(() -> activity.add(host.lastWindowActivityMillis("jagt", "ABC-4")));

    assertThat(activity).containsExactly(0L);
  }

  @Test
  void listsWindowsAgainForReadAfterTheTickEnded() {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(
        new Processes.Result(0, "jagt\t@1 100 1700000000 ABC-1\n", ""), "list-windows");
    TmuxReads reads = new TmuxReads(processes, ToolchainProperties.defaults());
    TmuxSessionHost host = tmuxHost().build(processes, reads, terminal, agent);

    reads.once(() -> host.lastWindowActivityMillis("jagt", "ABC-1"));
    host.lastWindowActivityMillis("jagt", "ABC-1");

    assertThat(processes.issuedContaining("list-windows")).hasSize(2);
  }

  @Test
  void acceptsSessionNameCarryingNeitherReservedCharacter() {
    assertThat(TmuxSessionHost.reserved("agents")).isEmpty();
  }

  @Test
  void namesTheSessionJagtWhereNoneIsConfigured() {
    TmuxSessionHost host = tmuxHost().build(mock(Processes.class), terminal, agent);

    assertThat(host.sessionName(null)).isEqualTo("jagt");
    assertThat(host.sessionName("  ")).isEqualTo("jagt");
    assertThat(host.sessionName("work")).isEqualTo("work");
  }

  @Test
  void refusesThroughTheHostSessionNameTmuxReserves() {
    TmuxSessionHost host = tmuxHost().build(mock(Processes.class), terminal, agent);

    assertThat(host.unaddressable("a:b")).isPresent();
    assertThat(host.unaddressable("ab")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"0, 4242, AGENT_RUNNING", "0, '', DEAD_SHELL", "1, '', DEAD_SHELL"})
  void tellsWindowWhoseAgentRunsFromOneLeftWithItsShell(
      int pgrepExit, String children, WindowState expected) {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(pgrepExit, children, ""), "pgrep");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(expected);
  }

  @Test
  void reportsMissingWindowWhereTheSessionListsNone() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.MISSING);
    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isZero();
  }

  @Test
  void readsTheLastOutputOfTheTasksOwnWindowNotOfOneWhoseNameEndsTheSame() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(
                new Processes.Result(0, "@1 100 1700000000 XABC-1\n@2 200 1700000500 ABC-1\n", ""));
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isEqualTo(1_700_000_500_000L);
  }

  @Test
  void killsEveryWindowOfTheTaskAndCountsTheOnesThatWentAway() {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(
        new Processes.Result(0, "@1 XABC-1\n@2 ABC-1\n@3 ABC-1\n@4 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(0, "", ""), "kill-window", "@2");
    processes.answering(new Processes.Result(0, "", ""), "kill-window", "@3");
    processes.answering(new Processes.Result(1, "", "gone"), "kill-window", "@4");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    int killed = host.killTaskWindows("jagt", "ABC-1");

    assertThat(killed).isEqualTo(2);
    List<List<String>> commands = processes.issued();
    assertThat(commands).noneMatch(command -> command.contains("@1"));
  }

  @Test
  void killsNothingWhereTheSessionListsNoWindow() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.killTaskWindows("jagt", "ABC-1")).isZero();
  }

  @Test
  void refusesToFocusWindowTheTaskDoesNotHave() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.focusTaskWindow("jagt", "agents", "ABC-1")).isFalse();
    verify(terminal, never()).openViewer(any(), any(), any());
  }

  @Test
  void selectsTheTasksWindowAndOpensTheViewerWhereNoClientIsAttached() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    processes.answering(
        new Processes.Result(0, "@1 100 1700000000 XABC-1\n@2 200 1700000500 ABC-1\n", ""),
        "list-windows");
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host = tmuxHost().withToolchain(properties).build(processes, terminal, agent);

    boolean focused = host.focusTaskWindow("jagt", "agents", "ABC-1");

    assertThat(focused).isTrue();
    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command ->
            assertThat(command).startsWith("/opt/jagt/tmux", "select-window", "-t", "@2"));
    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("agents"), any());
  }

  @Test
  void scopesTheWindowBarAndTitleOptionsToTheTasksSessionAlone() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    ToolchainProperties properties = ToolchainProperties.defaults().withTmuxCommand("tmux");
    TmuxSessionHost host = tmuxHost().withToolchain(properties).build(processes, terminal, agent);

    host.openCommandWindow("jagt", "jagt", "login", List.of("no-such-cli"));

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .filteredOn(command -> command.contains("set-titles"))
        .singleElement()
        .satisfies(options ->
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
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    processes.answering(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(0, "0", ""), "list-clients");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    host.focusTaskWindow("jagt", "agents", "ABC-1");

    verify(terminal, never()).openViewer(any(), any(), any());
  }

  @Test
  void opensViewerWhereOnlyControlClientIsAttached() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    processes.answering(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(0, "1", ""), "list-clients");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    host.focusTaskWindow("jagt", "agents", "ABC-1");

    verify(terminal).openViewer(any(), any(), any());
  }

  @Test
  void typesTheNudgeLiterallyThenPressesEnterInTheTasksWindow() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    processes.answering(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""), "list-windows");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    boolean nudged = host.nudgeTaskWindow("jagt", "ABC-1", "carry on");

    assertThat(nudged).isTrue();
    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command ->
            assertThat(command).containsSequence("send-keys", "-t", "@2", "-l", "carry on"));
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("send-keys", "-t", "@2", "Enter"));
  }

  @Test
  void nudgesNoWindowTheTaskDoesNotHave() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.nudgeTaskWindow("jagt", "ABC-1", "carry on")).isFalse();
  }

  @Test
  void submitsWhatTheInputHoldsByPressingEnterInTheTasksWindow() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    processes.answering(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""), "list-windows");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    boolean submitted = host.submitTaskWindow("jagt", "ABC-1");

    assertThat(submitted).isTrue();
    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("send-keys", "-t", "@2", "Enter"));
  }

  @Test
  void returnsTheScreenOfTheTasksWindow() {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(0, "$ build ok", ""), "capture-pane");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.screenOf("jagt", "ABC-1")).contains("$ build ok");
  }

  @Test
  void readsTheLastLinesAnAgentPrintedBeforeItExited() {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(1, "", ""), "pgrep");
    processes.answering(
        new Processes.Result(
            0,
            """
            Welcome
            Loading servers
            Error: Invalid MCP configuration:
              mcpServers.jagt: missing command

            [jagt] agent exited — window closes in 15s (Ctrl+C to close now)


            """,
            ""),
        "capture-pane");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

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
    FakeProcesses processes = new FakeProcesses();
    processes.answering(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(0, "4242\n", ""), "pgrep");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.exitTail("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void reportsNoScreenForTaskWithoutWindow() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.screenOf("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void launchesTheAgentWithoutModelFlagWhereNoModelIsAsked() {
    when(agent.launchCommand(Path.of("/wt/ABC-1"), false)).thenReturn("agent-cmd");

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command -> {
          assertThat(command).containsSequence("new-window", "-d", "-P");
          assertThat(command.getLast()).contains("agent-cmd").endsWith("; exec sleep 15");
        });
  }

  @Test
  void opensTheTasksWindowWithoutSelectingItWhereTheHumanWorksInAnother() {
    when(agent.launchCommand(Path.of("/wt/ABC-2"), false)).thenReturn("agent-cmd");

    host.openTaskWindow("jagt", "agents", "ABC-2", null, Path.of("/wt/ABC-2"), false, "");

    assertThat(processes.issuedContaining("new-window"))
        .singleElement()
        .satisfies(command -> assertThat(command).contains("-d"));
    assertThat(processes.issuedContaining("select-window")).isEmpty();
  }

  @Test
  void restoresWhatTheAgentWasProvisionedWithBeforeOpeningItsWindow() {
    doAnswer(
            restoring -> {
              assertThat(processes.issuedContaining("new-window")).isEmpty();
              return null;
            })
        .when(agent)
        .beforeLaunch(Path.of("/wt/ABC-1"));

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    verify(agent).beforeLaunch(Path.of("/wt/ABC-1"));
    assertThat(processes.issuedContaining("new-window")).hasSize(1);
  }

  @Test
  void launchesTheAgentOnTheModelAskedFor() {
    when(agent.launchCommand(Path.of("/wt/ABC-1"), true, "opus")).thenReturn("agent-on-opus");

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), true, "opus");

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command -> assertThat(command.getLast()).contains("agent-on-opus"));
  }

  @Test
  void carriesTheAliasInWindowOptionAndLeavesTheWindowNameTheTask() {

    host.openTaskWindow("jagt", "agents", "ABC-1", "login fix", Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command ->
            assertThat(command)
                .containsSequence("set-option", "-w", "-t", "@9", "@jagt_alias", "login fix"));
    assertThat(commands).anySatisfy(command -> assertThat(command).containsSequence("-n", "ABC-1"));
  }

  @Test
  void setsNoAliasOptionOnWindowWhoseTaskHasNone() {

    host.openTaskWindow("jagt", "agents", "ABC-1", "  ", Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = processes.issued();
    assertThat(commands).noneMatch(command -> command.contains("@jagt_alias"));
  }

  @Test
  void createsTheSessionWhereNoneExistsYet() {
    processes.answering(new Processes.Result(1, "", "no session"), "has-session");

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
  }

  @Test
  void createsNoSessionWhereOneExists() {

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = processes.issued();
    assertThat(commands).noneMatch(command -> command.contains("new-session"));
  }

  @Test
  void killsTheTasksEarlierWindowBeforeOpeningItsNewOne() {
    processes.answering(new Processes.Result(0, "@4 ABC-1\n", ""), "list-windows");

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command -> assertThat(command).containsSequence("kill-window", "-t", "@4"));
  }

  @Test
  void opensTheViewerWhereTaskWindowIsOpenedAndNoClientIsAttached() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "@9", ""));
    processes.answering(new Processes.Result(0, "", ""), "list-clients");
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host = tmuxHost().withToolchain(properties).build(processes, terminal, agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("agents"), any());
  }

  @Test
  void revivesTheTaskInWindowRunningTheRuntimesReviveCommand() {
    when(agent.reviveCommand(Path.of("/wt/ABC-1"))).thenReturn("agent --continue");

    host.reviveTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"));

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command -> {
          assertThat(command).containsSequence("new-window", "-d", "-P");
          assertThat(command.getLast()).contains("agent --continue");
        });
  }

  @Test
  void runsCommandAsTheProcessOfWindowOfItsOwnQuotedWordByWord() {

    host.openCommandWindow("jagt", "jagt", "login", List.of("no-such-cli", "auth", "it's"));

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command -> {
          assertThat(command).containsSequence("new-window", "-d", "-P");
          assertThat(command.getLast()).startsWith("'no-such-cli' 'auth' 'it'\\''s';");
        });
  }

  @Test
  void runsShellLineInTheFolderGivenAsTheProcessOfWindowOfItsOwn() {

    host.openShellWindow("jagt", "jagt", "acceptance-alpha", Path.of("/ws"), "agent 'p'");

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command -> {
          assertThat(command).containsSequence("new-window", "-d", "-P");
          assertThat(command).containsSequence("-n", "acceptance-alpha", "-c", "/ws");
          assertThat(command.getLast()).startsWith("agent 'p';");
        });
  }

  @Test
  void createsTheSessionAndShowsItToTheHumanForCommandWindow() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "@9", ""));
    processes.answering(new Processes.Result(1, "", "no session"), "has-session");
    processes.answering(new Processes.Result(0, "", ""), "list-clients");
    ToolchainProperties properties =
        ToolchainProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
    TmuxSessionHost host = tmuxHost().withToolchain(properties).build(processes, terminal, agent);

    host.openCommandWindow("jagt", "jagt-title", "login", List.of("no-such-cli"));

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
    verify(terminal)
        .openViewer(eq(List.of("/opt/jagt/tmux", "attach", "-t", "jagt")), eq("jagt-title"), any());
  }

  @Test
  void submitsNothingWhereTheTaskHasNoWindow() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.submitTaskWindow("jagt", "ABC-1")).isFalse();
  }

  @Test
  void readsAnEmptyTailWhereTheAgentExitedPrintingNothing() {
    FakeProcesses processes = new FakeProcesses();
    processes.answering(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""), "list-windows");
    processes.answering(new Processes.Result(1, "", ""), "pgrep");
    processes.answering(
        new Processes.Result(
            0, "[jagt] agent exited — window closes in 15s (Ctrl+C to close now)\n", ""),
        "capture-pane");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.exitTail("jagt", "ABC-1")).contains("");
  }

  @ParameterizedTest
  @CsvSource({
    "automatic-rename,no such window,tmux window rename failed",
    "escape-time,unknown option,tmux session options failed"
  })
  void warnsWhereTmuxStepOfOpeningTheWindowFailed(String step, String error, String event) {
    processes.answering(new Processes.Result(1, "", error), step);
    try (FakeLogCapture log = new FakeLogCapture(TmuxSessionHost.class)) {
      host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

      assertThat(log.events())
          .filteredOn(logged -> event.equals(logged.getMessage()))
          .extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.WARN);
    }
  }

  @Test
  void opensNoTaskWindowWhereTheSessionsWindowsCannotBeListed() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    processes.answering(new Processes.Result(1, "", "server timed out"), "list-windows");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThatThrownBy(() ->
            host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, ""))
        .isInstanceOf(IllegalStateException.class);
    assertThat(processes.issuedContaining("new-window")).isEmpty();
  }

  @Test
  void startsTaskSessionUnderItsOwnComposeProject() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "@9", ""));
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, ToolchainProperties.defaults()),
            new SessionEnvironment(
                new OrchestratorPaths(OrchestratorProperties.defaults(), FakeHost.machine()),
                new DockerComposeContainers(processes),
                FakeHost.machine()),
            terminal,
            agent);

    host.openTaskWindow(
        "jagt", "agents", "ABC-1", null, Path.of("/data/tasks/ABC-1/worktrees/Shop"), false, "");

    assertThat(processes.issuedContaining("new-window"))
        .singleElement()
        .satisfies(command ->
            assertThat(command.getLast()).startsWith("COMPOSE_PROJECT_NAME='shop-abc-1' "));
  }

  @Test
  void stopsGitClimbingOutOfScratchFolderWhereTheSessionRuns() {
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "@9", ""));
    ToolchainProperties properties = ToolchainProperties.defaults();
    OrchestratorPaths paths =
        new OrchestratorPaths(OrchestratorProperties.defaults(), FakeHost.machine());
    TmuxSessionHost host =
        new TmuxSessionHost(
            processes,
            new TmuxReads(processes, properties),
            new SessionEnvironment(paths, new NoWorktreeContainers(), FakeHost.machine()),
            terminal,
            agent);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, paths.scratch("ABC-1"), true, "");

    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(command ->
            assertThat(command.getLast())
                .startsWith("GIT_CEILING_DIRECTORIES='" + paths.tasks() + "' "));
  }

  @Test
  void leavesGitsCeilingAloneForWorktreeOfRepository() {

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

    List<List<String>> commands = processes.issued();
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
    FakeProcesses processes = new FakeProcesses().answering(new Processes.Result(0, "", ""));
    processes.answering(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""), "list-windows");
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    boolean pressed = host.pressTaskWindow("jagt", "ABC-1", key);

    assertThat(pressed).isTrue();
    List<List<String>> commands = processes.issued();
    assertThat(commands)
        .anySatisfy(
            command -> assertThat(command).containsSequence("send-keys", "-t", "@2", tmuxName))
        .noneSatisfy(command -> assertThat(command).contains("-l"));
  }

  @Test
  void pressesNoKeyWhereTheTaskHasNoWindow() {
    FakeProcesses processes =
        new FakeProcesses()
            .answering(new Processes.Result(1, "", "no server running on /tmp/tmux-501/default"));
    TmuxSessionHost host = tmuxHost().build(processes, terminal, agent);

    assertThat(host.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.ESCAPE)).isFalse();
  }

  @Nested
  class OnConfiguredSocket {

    private final FakeProcesses socketProcesses =
        new FakeProcesses().answering(new Processes.Result(0, "", ""));
    private TmuxSessionHost socketHost;

    @BeforeEach
    void setUp() {
      socketProcesses.answering(
          new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""), "list-windows");
      ToolchainProperties properties =
          ToolchainProperties.defaults()
              .withTmuxCommand("/opt/jagt/tmux")
              .withTmuxSocket("jagt-sock");
      socketHost = tmuxHost().withToolchain(properties).build(socketProcesses, terminal, agent);
    }

    @Test
    void everyTmuxCommandNamesTheServer() {
      socketHost.focusTaskWindow("jagt", "agents", "ABC-1");

      assertThat(socketProcesses.issued())
          .isNotEmpty()
          .allSatisfy(
              command -> assertThat(command).startsWith("/opt/jagt/tmux", "-L", "jagt-sock"));
    }

    @Test
    void theViewerAttachesToTheSession() {
      socketHost.focusTaskWindow("jagt", "agents", "ABC-1");

      verify(terminal)
          .openViewer(
              eq(List.of("/opt/jagt/tmux", "-L", "jagt-sock", "attach", "-t", "jagt")),
              eq("agents"),
              any());
    }
  }
}
