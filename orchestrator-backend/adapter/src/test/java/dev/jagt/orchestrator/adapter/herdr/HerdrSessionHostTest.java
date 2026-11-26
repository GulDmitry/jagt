package dev.jagt.orchestrator.adapter.herdr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.adapter.terminal.SessionEnvironment;
import dev.jagt.orchestrator.adapter.worktree.NoWorktreeContainers;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.agent.LaunchMode;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import dev.jagt.orchestrator.port.terminal.SessionHost.WindowState;
import dev.jagt.orchestrator.port.terminal.SessionHost.WindowsUnreadable;
import dev.jagt.orchestrator.port.terminal.TerminalDriver;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class HerdrSessionHostTest {

  private final TerminalDriver terminal = mock(TerminalDriver.class);
  private final AgentRuntime agent = mock(AgentRuntime.class);
  private final Clock clock = mock(Clock.class);
  private final FakeProcesses processes =
      new FakeProcesses().answering(new Processes.Result(0, "", ""));
  private FakeHerdrWire herdr;
  private HerdrReads reads;
  private HerdrSessionHost host;

  @BeforeEach
  void startsHerdr() throws Exception {
    herdr = new FakeHerdrWire();
    HerdrApi api =
        new HerdrApi(
            FakeHost.machine(),
            new HerdrProperties("/bin/herdr", "/state/herdr"),
            processes,
            herdr);
    reads = new HerdrReads(api, clock);
    host =
        new HerdrSessionHost(
            api,
            reads,
            new SessionEnvironment(
                new OrchestratorPaths(OrchestratorProperties.defaults(), FakeHost.machine()),
                new NoWorktreeContainers(),
                FakeHost.machine()),
            terminal,
            agent);
    when(agent.launchCommand(any(), eq(LaunchMode.WORK))).thenReturn("agent --go");
    when(agent.reviveCommand(any())).thenReturn("agent --continue");
  }

  @Test
  void opensTheTaskInTabsLabelledWithItsNameRunningTheAgentLine() {
    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.AGENT_RUNNING);
    assertThat(herdr.screenOf("ABC-1")).contains("exec sh -c ", "agent --go");
  }

  @Test
  void replacesTheWindowOfTheSameNameInsteadOfAddingMore() {
    herdr.withTab("ABC-1", true);

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");

    assertThat(herdr.requests().stream().filter(r -> r.method().equals("tab.close"))).hasSize(1);
    assertThat(host.killTaskWindows("jagt", "ABC-1")).isEqualTo(1);
  }

  @Test
  void revivesByReEnteringTheSessionWithItsReviveCommand() {
    host.reviveTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"));

    assertThat(herdr.screenOf("ABC-1")).contains("agent --continue");
  }

  @Test
  void carriesTheAliasOnThePaneNotOnTheWindowName() {
    host.openTaskWindow("jagt", "agents", "ABC-1", "login", Path.of("/tmp"), LaunchMode.WORK, "");

    assertThat(herdr.requests())
        .anyMatch(r ->
            r.method().equals("pane.rename")
                && r.params().path("label").asString("").equals("login"));
  }

  @Test
  void opensTheViewerWhereNoClientOfTheSessionRuns() {
    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");

    verify(terminal)
        .openViewer(
            eq(List.of("env", "XDG_CONFIG_HOME=/state", "/bin/herdr", "session", "attach", "jagt")),
            eq("agents"),
            any());
  }

  @Test
  void opensTheViewerWhereOnlyOtherSessionsHaveClients() {
    processes.answering(
        new Processes.Result(0, "/opt/homebrew/bin/herdr session attach other\n", ""), "ps");

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");

    verify(terminal).openViewer(any(), eq("agents"), any());
  }

  @Test
  void opensNoSecondViewerWhereClientsOfTheSessionRun() {
    processes.answering(
        new Processes.Result(0, "/opt/homebrew/bin/herdr session attach jagt\n", ""), "ps");

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");

    verify(terminal, never()).openViewer(any(), any(), any());
  }

  @Test
  void reportsMissingWhereNoServerListensAtAll() {
    herdr.down();

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.MISSING);
  }

  @Test
  void reportsMissingWhereTheSessionHoldsNoSuchWindow() {
    herdr.withTab("ABC-2", true);

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.MISSING);
  }

  @Test
  void reportsTheAgentRunningWhileItsTypedLineIsAboutToRun() {
    herdr.withTypedLineNotYetRun("ABC-1");

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.AGENT_RUNNING);
  }

  @Test
  void reportsDeadShellOnceTheAgentIsGone() {
    herdr.withTab("ABC-1", false);

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.DEAD_SHELL);
  }

  @Test
  void decidesNothingWhereTheWindowsCannotBeListed() {
    herdr.withTab("ABC-1", true).refusing("tab.list", "internal_error");

    assertThatThrownBy(() -> host.taskWindowState("jagt", "ABC-1"))
        .isInstanceOf(WindowsUnreadable.class);
  }

  @Test
  void opensNoWindowWhereTheWindowsCannotBeListed() {
    herdr.withTab("ABC-1", true).refusing("tab.list", "internal_error");

    assertThatThrownBy(() ->
            host.openTaskWindow(
                "jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, ""))
        .isInstanceOf(WindowsUnreadable.class);
    assertThat(herdr.requests().stream().filter(r -> r.method().equals("tab.create"))).isEmpty();
  }

  @Test
  void readsTheTailTheAgentPrintedBeforeItExited() {
    herdr
        .withTab("ABC-1", false)
        .showing("ABC-1", "one\ntwo\nthree\nfour\n[jagt] agent exited — window closes in 15s\n");

    assertThat(host.exitTail("jagt", "ABC-1")).contains("two\nthree\nfour");
  }

  @Test
  void readsNoTailWhileTheAgentRuns() {
    herdr.withTab("ABC-1", true).showing("ABC-1", "one\n[jagt] agent exited\n");

    assertThat(host.exitTail("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void answersTheScreenWithItsStylingKept() {
    herdr.withTab("ABC-1", true).showing("ABC-1", "\u001b[31mred\u001b[0m");

    assertThat(host.screenOf("jagt", "ABC-1")).contains("\u001b[31mred\u001b[0m");
  }

  @Test
  void answersNoScreenForWindowsTheSessionDoesNotHold() {
    assertThat(host.screenOf("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void typesTheLineAndPressesEnterInOneInput() {
    herdr.withTab("ABC-1", true);

    boolean nudged = host.nudgeTaskWindow("jagt", "ABC-1", "continue");

    assertThat(nudged).isTrue();
    assertThat(herdr.screenOf("ABC-1")).endsWith("continue<enter>");
  }

  @Test
  void refusesToNudgeWindowsTheSessionDoesNotHold() {
    assertThat(host.nudgeTaskWindow("jagt", "ABC-1", "continue")).isFalse();
  }

  @ParameterizedTest
  @EnumSource(SessionHost.Key.class)
  void pressesEveryKeyHumansCanPress(SessionHost.Key key) {
    herdr.withTab("ABC-1", true);

    assertThat(host.pressTaskWindow("jagt", "ABC-1", key)).isTrue();
  }

  @Test
  void submitsWithEnter() {
    herdr.withTab("ABC-1", true);

    host.submitTaskWindow("jagt", "ABC-1");

    assertThat(herdr.screenOf("ABC-1")).endsWith("<enter>");
  }

  @Test
  void closesEveryWindowOfTheTaskAndCountsThem() {
    herdr.withTab("ABC-1", true).withTab("ABC-1", true).withTab("ABC-2", true);

    assertThat(host.killTaskWindows("jagt", "ABC-1")).isEqualTo(2);
    assertThat(host.taskWindowState("jagt", "ABC-2")).isEqualTo(WindowState.AGENT_RUNNING);
  }

  @Test
  void focusesTheTaskTabAndOpensTheViewer() {
    herdr.withTab("ABC-1", true);

    boolean focused = host.focusTaskWindow("jagt", "agents", "ABC-1");

    assertThat(focused).isTrue();
    assertThat(herdr.requests()).anyMatch(r -> r.method().equals("tab.focus"));
    verify(terminal).openViewer(any(), eq("agents"), any());
  }

  @Test
  void focusesNothingWhereTheTaskHasNoWindow() {
    assertThat(host.focusTaskWindow("jagt", "agents", "ABC-1")).isFalse();
  }

  @Test
  void countsActivityFromTheMomentTheScreenLastChanged() {
    herdr.withTab("ABC-1", true).showing("ABC-1", "first");
    when(clock.millis()).thenReturn(1000L, 2000L, 3000L);
    List<Long> seen = new ArrayList<>();

    seen.add(host.lastWindowActivityMillis("jagt", "ABC-1"));
    seen.add(host.lastWindowActivityMillis("jagt", "ABC-1"));
    herdr.showing("ABC-1", "second");
    seen.add(host.lastWindowActivityMillis("jagt", "ABC-1"));

    assertThat(seen).containsExactly(1000L, 1000L, 2000L);
  }

  @Test
  void reportsNoActivityForWindowsTheSessionDoesNotHold() {
    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isZero();
  }

  @Test
  void listsTheSessionOnceForEveryTaskOfTick() {
    herdr.withTab("ABC-1", true).withTab("ABC-2", true).withTab("ABC-3", true);
    List<WindowState> states = new ArrayList<>();

    reads.once(() -> {
      states.add(host.taskWindowState("jagt", "ABC-1"));
      states.add(host.taskWindowState("jagt", "ABC-2"));
      states.add(host.taskWindowState("jagt", "ABC-3"));
    });

    assertThat(states).containsOnly(WindowState.AGENT_RUNNING);
    assertThat(herdr.requests().stream().filter(r -> r.method().equals("tab.list"))).hasSize(1);
  }

  @Test
  void listsAgainWhereWindowsChangedWithinTheTick() {
    herdr.withTab("ABC-1", true);

    reads.once(() -> {
      host.taskWindowState("jagt", "ABC-1");
      host.submitTaskWindow("jagt", "ABC-1");
      host.taskWindowState("jagt", "ABC-1");
    });

    assertThat(herdr.requests().stream().filter(r -> r.method().equals("tab.list"))).hasSize(2);
  }

  @Test
  void opensCommandWindowsRunningTheQuotedCommandAndSelectsThem() {
    host.openCommandWindow("jagt", "agents", "login", List.of("/bin/echo", "it's"));

    assertThat(herdr.screenOf("login")).contains("exec sh -c ", "/bin/echo", "it");
    assertThat(herdr.requests()).anyMatch(r -> r.method().equals("tab.focus"));
  }

  @Test
  void opensShellWindowsRunningTheLineAndSelectsThem() {
    host.openShellWindow("jagt", "agents", "build", Path.of("/tmp"), "make all");

    assertThat(herdr.screenOf("build")).contains("make all");
    verify(terminal).openViewer(any(), eq("agents"), any());
  }

  @Test
  void opensNoShellWindowWhereTheServerRefusesToFocusIt() {
    herdr.refusing("tab.focus", "tab_not_found");

    assertThatThrownBy(
            () -> host.openShellWindow("jagt", "agents", "build", Path.of("/tmp"), "make"))
        .isInstanceOf(HerdrApi.Failed.class);
  }

  @Test
  void reportsMissingWhereThePaneClosedBetweenListingAndReading() {
    herdr.withTab("ABC-1", true).refusing("pane.process_info", "pane_not_found");

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.MISSING);
  }

  @Test
  void refusesToPressInPanesThatClosedBetweenListingAndPressing() {
    herdr.withTab("ABC-1", true).refusing("pane.send_keys", "pane_not_found");

    assertThat(host.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.ENTER)).isFalse();
  }

  @Test
  void refusesToPressInWindowsTheSessionDoesNotHold() {
    assertThat(host.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.ENTER)).isFalse();
  }

  @Test
  void reportsTheEnterSentWhereTheWindowIsThere() {
    herdr.withTab("ABC-1", true);

    assertThat(host.submitTaskWindow("jagt", "ABC-1")).isTrue();
  }

  @Test
  void refusesToSubmitInWindowsTheSessionDoesNotHold() {
    assertThat(host.submitTaskWindow("jagt", "ABC-1")).isFalse();
  }

  @Test
  void countsNoWindowClosedWhereTheServerRefusesTheClose() {
    herdr.withTab("ABC-1", true).refusing("tab.close", "tab_not_found");

    assertThat(host.killTaskWindows("jagt", "ABC-1")).isZero();
  }

  @Test
  void readsTheScreenStyledForTheHumanAndPlainForTheExitTail() {
    herdr.withTab("ABC-1", false).showing("ABC-1", "x\n[jagt] agent exited\n");

    host.screenOf("jagt", "ABC-1");
    host.exitTail("jagt", "ABC-1");

    assertThat(herdr.requests().stream().filter(r -> r.method().equals("pane.read")))
        .extracting(r -> r.params().path("format").asString("") + r.params().path("strip_ansi"))
        .containsExactly("ansifalse", "texttrue", "texttrue");
  }

  @Test
  void reportsNoActivityWhereThePaneClosedBetweenListingAndReading() {
    herdr.withTab("ABC-1", true).refusing("pane.read", "pane_not_found");

    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isZero();
  }

  @Test
  void listsAgainOnceWindowsAreClosedWithinTheTick() {
    herdr.withTab("ABC-1", true);
    List<WindowState> states = new ArrayList<>();

    reads.once(() -> {
      states.add(host.taskWindowState("jagt", "ABC-1"));
      host.killTaskWindows("jagt", "ABC-1");
      states.add(host.taskWindowState("jagt", "ABC-1"));
    });

    assertThat(states).containsExactly(WindowState.AGENT_RUNNING, WindowState.MISSING);
  }

  @Test
  void listsAgainOnceWindowsAreOpenedWithinTheTick() {
    List<WindowState> states = new ArrayList<>();

    reads.once(() -> {
      states.add(host.taskWindowState("jagt", "ABC-1"));
      host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");
      states.add(host.taskWindowState("jagt", "ABC-1"));
    });

    assertThat(states).containsExactly(WindowState.MISSING, WindowState.AGENT_RUNNING);
  }

  @Test
  void runsNestedTicksInTheOuterOne() {
    List<String> ran = new ArrayList<>();

    reads.once(() -> reads.once(() -> ran.add("inner")));

    assertThat(ran).containsExactly("inner");
  }

  @Test
  void decidesNothingWhereThePaneProcessesCannotBeRead() {
    herdr.withTab("ABC-1", true).refusing("pane.process_info", "internal_error");

    assertThatThrownBy(() -> host.taskWindowState("jagt", "ABC-1"))
        .isInstanceOf(WindowsUnreadable.class);
  }

  @Test
  void decidesNothingWhereTheServerDiesBetweenListingAndReading() {
    herdr.withTab("ABC-1", true).downAfter("tab.list");

    assertThatThrownBy(() -> host.taskWindowState("jagt", "ABC-1"))
        .isInstanceOf(WindowsUnreadable.class);
  }

  @Test
  void readsNoTailWhereTheScreenCannotBeRead() {
    herdr.withTab("ABC-1", false).refusing("pane.read", "internal_error");

    assertThatThrownBy(() -> host.exitTail("jagt", "ABC-1")).isInstanceOf(WindowsUnreadable.class);
  }

  @Test
  void pressesNoKeyWhereTheServerRefusesIt() {
    herdr.withTab("ABC-1", true).refusing("pane.send_keys", "internal_error");

    assertThatThrownBy(() -> host.pressTaskWindow("jagt", "ABC-1", SessionHost.Key.ENTER))
        .isInstanceOf(WindowsUnreadable.class);
  }

  @Test
  void countsNoActivityWhereTheScreenCannotBeRead() {
    herdr.withTab("ABC-1", true).refusing("pane.read", "internal_error");

    assertThatThrownBy(() -> host.lastWindowActivityMillis("jagt", "ABC-1"))
        .isInstanceOf(WindowsUnreadable.class);
  }

  @Test
  void answersNoScreenWhereThePaneClosedBetweenListingAndReading() {
    herdr.withTab("ABC-1", true).refusing("pane.read", "pane_not_found");

    assertThat(host.screenOf("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void readsNoTailWhereThePaneClosedBetweenListingAndReading() {
    herdr.withTab("ABC-1", false).refusing("pane.read", "pane_not_found");

    assertThat(host.exitTail("jagt", "ABC-1")).isEmpty();
  }

  @Test
  void reportsTheAgentRunningWhereTheShellHasNotEchoedItsLineYet() {
    when(clock.millis()).thenReturn(1000L);
    herdr.slowShell();

    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.AGENT_RUNNING);
  }

  @Test
  void reportsTheAgentRunningOnceTheShellEchoedItsLine() {
    when(clock.millis()).thenReturn(1000L);
    herdr.slowShell();
    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");

    herdr.shellEchoes();

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.AGENT_RUNNING);
  }

  @Test
  void reportsDeadShellWhereTheShellNeverEchoedItsLineWithinTheStartWindow() {
    when(clock.millis()).thenReturn(1000L, 61000L);
    herdr.slowShell();
    host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/tmp"), LaunchMode.WORK, "");

    assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.DEAD_SHELL);
  }

  @Test
  void listsOnceForEveryTaskOfTickWhereTheListingFails() {
    herdr.withTab("ABC-1", true).withTab("ABC-2", true).refusing("tab.list", "internal_error");

    reads.once(() -> {
      assertThatThrownBy(() -> host.taskWindowState("jagt", "ABC-1"))
          .isInstanceOf(WindowsUnreadable.class);
      assertThatThrownBy(() -> host.taskWindowState("jagt", "ABC-2"))
          .isInstanceOf(WindowsUnreadable.class);
    });

    assertThat(herdr.requests().stream().filter(r -> r.method().equals("tab.list"))).hasSize(1);
  }

  @Test
  void countsActivityAnewForTaskWindowReopenedAfterItWasKilled() {
    when(clock.millis()).thenReturn(1000L, 2000L);
    herdr.withTab("ABC-1", true).showing("ABC-1", "same");
    host.lastWindowActivityMillis("jagt", "ABC-1");
    host.killTaskWindows("jagt", "ABC-1");
    herdr.withTab("ABC-1", true).showing("ABC-1", "same");

    assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isEqualTo(2000L);
  }

  @Test
  void namesTheSessionJagtWhereNoneIsConfigured() {
    assertThat(host.sessionName(" ")).isEqualTo("jagt");
  }
}
