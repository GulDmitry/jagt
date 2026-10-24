package dev.jagt.orchestrator.adapter.tmux;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.SessionHost.WindowState;
import dev.jagt.orchestrator.port.TerminalDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TmuxSessionHostTest {

    @ParameterizedTest
    @ValueSource(strings = {"jagt:agents", "jagt.agents"})
    void refusesASessionNameTmuxWouldReadAsAWindowOrAPane(String name) {
        assertThat(TmuxSessionHost.reserved(name)).contains("contains ':' or '.', which tmux reserves");
    }

    @Test
    void throwsRatherThanReportingNoWindowWhenTheWindowIsThereButItsScreenCannotBeRead() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("capture-pane")))).thenReturn(new Processes.Result(1, "", "can't find pane"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThatThrownBy(() -> host.screenOf("jagt", "ABC-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsASessionNameCarryingNeitherReservedCharacter() {
        assertThat(TmuxSessionHost.reserved("agents")).isEmpty();
    }

    @Test
    void namesTheSessionJagtWhereNoneIsConfigured() {
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(mock(Processes.class), properties,
                new OrchestratorPaths(properties), mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.sessionName(null)).isEqualTo("jagt");
        assertThat(host.sessionName("  ")).isEqualTo("jagt");
        assertThat(host.sessionName("work")).isEqualTo("work");
    }

    @Test
    void refusesThroughTheHostASessionNameTmuxReserves() {
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(mock(Processes.class), properties,
                new OrchestratorPaths(properties), mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.unaddressable("a:b")).isPresent();
        assertThat(host.unaddressable("ab")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0, 4242, AGENT_RUNNING", "0, '', DEAD_SHELL", "1, '', DEAD_SHELL"})
    void tellsAWindowWhoseAgentRunsFromOneLeftWithItsShell(int pgrepExit, String children, WindowState expected) {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("pgrep")))).thenReturn(new Processes.Result(pgrepExit, children, ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(expected);
    }

    @Test
    void reportsAMissingWindowWhereTheSessionListsNone() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "no server"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.taskWindowState("jagt", "ABC-1")).isEqualTo(WindowState.MISSING);
        assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isZero();
    }

    @Test
    void readsTheLastOutputOfTheTasksOwnWindowNotOfOneWhoseNameEndsTheSame() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0,
                "@1 100 1700000000 XABC-1\n@2 200 1700000500 ABC-1\n", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.lastWindowActivityMillis("jagt", "ABC-1")).isEqualTo(1_700_000_500_000L);
    }

    @Test
    void killsEveryWindowOfTheTaskAndCountsTheOnesThatWentAway() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0,
                "@1 XABC-1\n@2 ABC-1\n@3 ABC-1\n@4 ABC-1\n", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("kill-window") && command.contains("@2")))).thenReturn(new Processes.Result(0, "", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("kill-window") && command.contains("@3")))).thenReturn(new Processes.Result(0, "", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("kill-window") && command.contains("@4")))).thenReturn(new Processes.Result(1, "", "gone"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        int killed = host.killTaskWindows("jagt", "ABC-1");

        assertThat(killed).isEqualTo(2);
        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).noneMatch(command -> command.contains("@1"));
    }

    @Test
    void killsNothingWhereTheSessionListsNoWindow() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "no server"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.killTaskWindows("jagt", "ABC-1")).isZero();
    }

    @Test
    void refusesToFocusAWindowTheTaskDoesNotHave() {
        Processes processes = mock(Processes.class);
        TerminalDriver terminal = mock(TerminalDriver.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "no server"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                terminal, mock(AgentRuntime.class));

        assertThat(host.focusTaskWindow("jagt", "agents", "ABC-1")).isFalse();
        verify(terminal, never()).openViewer(any(), any(), any());
    }

    @Test
    void selectsTheTasksWindowAndOpensTheViewerWhereNoClientIsAttached() {
        Processes processes = mock(Processes.class);
        TerminalDriver terminal = mock(TerminalDriver.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0,
                "@1 100 1700000000 XABC-1\n@2 200 1700000500 ABC-1\n", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults().withTmuxCommand("/opt/jagt/tmux");
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                terminal, mock(AgentRuntime.class));

        boolean focused = host.focusTaskWindow("jagt", "agents", "ABC-1");

        assertThat(focused).isTrue();
        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).startsWith("/opt/jagt/tmux", "select-window", "-t", "@2"));
        verify(terminal).openViewer(eq("jagt"), eq("agents"), any());
    }

    @Test
    void opensNoSecondViewerWhereAClientIsAlreadyAttached() {
        Processes processes = mock(Processes.class);
        TerminalDriver terminal = mock(TerminalDriver.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-clients")))).thenReturn(new Processes.Result(0, "/dev/ttys001: jagt", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                terminal, mock(AgentRuntime.class));

        host.focusTaskWindow("jagt", "agents", "ABC-1");

        verify(terminal, never()).openViewer(any(), any(), any());
    }

    @Test
    void typesTheNudgeLiterallyThenPressesEnterInTheTasksWindow() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        boolean nudged = host.nudgeTaskWindow("jagt", "ABC-1", "carry on");

        assertThat(nudged).isTrue();
        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).containsSequence("send-keys", "-t", "@2", "-l", "carry on"));
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).containsSequence("send-keys", "-t", "@2", "Enter"));
    }

    @Test
    void nudgesNoWindowTheTaskDoesNotHave() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "no server"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.nudgeTaskWindow("jagt", "ABC-1", "carry on")).isFalse();
    }

    @Test
    void submitsWhatTheInputHoldsByPressingEnterInTheTasksWindow() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        boolean submitted = host.submitTaskWindow("jagt", "ABC-1");

        assertThat(submitted).isTrue();
        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).containsSequence("send-keys", "-t", "@2", "Enter"));
    }

    @Test
    void returnsTheScreenOfTheTasksWindow() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@2 200 1700000500 ABC-1\n", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("capture-pane")))).thenReturn(new Processes.Result(0, "$ build ok", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.screenOf("jagt", "ABC-1")).contains("$ build ok");
    }

    @Test
    void readsTheLastLinesAnAgentPrintedBeforeItExited() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("pgrep")))).thenReturn(new Processes.Result(1, "", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("capture-pane")))).thenReturn(new Processes.Result(0, """
                Welcome
                Loading servers
                Error: Invalid MCP configuration:
                  mcpServers.jagt: missing command

                [jagt] agent exited — window closes in 15s (Ctrl+C to close now)


                """, ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.exitTail("jagt", "ABC-1"))
                .contains("Loading servers\nError: Invalid MCP configuration:\n  mcpServers.jagt: missing command");
    }

    @Test
    void readsNoExitWhileTheAgentRuns() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@1 100 1700000000 ABC-1\n", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("pgrep")))).thenReturn(new Processes.Result(0, "4242\n", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.exitTail("jagt", "ABC-1")).isEmpty();
    }

    @Test
    void reportsNoScreenForATaskWithoutAWindow() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(1, "", "no server"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        assertThat(host.screenOf("jagt", "ABC-1")).isEmpty();
    }

    @Test
    void launchesTheAgentWithoutAModelFlagWhereNoModelIsAsked() {
        Processes processes = mock(Processes.class);
        AgentRuntime agent = mock(AgentRuntime.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        when(agent.launchCommand(Path.of("/wt/ABC-1"), false)).thenReturn("agent-cmd");
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agent);

        host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command -> {
            assertThat(command).containsSequence("new-window", "-P");
            assertThat(command.getLast()).contains("agent-cmd").endsWith("; exec sleep 15");
        });
    }

    @Test
    void launchesTheAgentOnTheModelAskedFor() {
        Processes processes = mock(Processes.class);
        AgentRuntime agent = mock(AgentRuntime.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        when(agent.launchCommand(Path.of("/wt/ABC-1"), true, "opus")).thenReturn("agent-on-opus");
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agent);

        host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), true, "opus");

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command.getLast()).contains("agent-on-opus"));
    }

    @Test
    void carriesTheAliasInAWindowOptionAndLeavesTheWindowNameTheTask() {
        Processes processes = mock(Processes.class);
        AgentRuntime agent = mock(AgentRuntime.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agent);

        host.openTaskWindow("jagt", "agents", "ABC-1", "login fix", Path.of("/wt/ABC-1"), false, "");

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).containsSequence("set-option", "-w", "-t", "@9", "@jagt_alias", "login fix"));
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).containsSequence("-n", "ABC-1"));
    }

    @Test
    void setsNoAliasOptionOnAWindowWhoseTaskHasNone() {
        Processes processes = mock(Processes.class);
        AgentRuntime agent = mock(AgentRuntime.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agent);

        host.openTaskWindow("jagt", "agents", "ABC-1", "  ", Path.of("/wt/ABC-1"), false, "");

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).noneMatch(command -> command.contains("@jagt_alias"));
    }

    @Test
    void createsTheSessionWhereNoneExistsYet() {
        Processes processes = mock(Processes.class);
        AgentRuntime agent = mock(AgentRuntime.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("has-session")))).thenReturn(new Processes.Result(1, "", "no session"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agent);

        host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
    }

    @Test
    void createsNoSessionWhereOneExists() {
        Processes processes = mock(Processes.class);
        AgentRuntime agent = mock(AgentRuntime.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agent);

        host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).noneMatch(command -> command.contains("new-session"));
    }

    @Test
    void killsTheTasksEarlierWindowBeforeOpeningItsNewOne() {
        Processes processes = mock(Processes.class);
        AgentRuntime agent = mock(AgentRuntime.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0, "@4 ABC-1\n", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agent);

        host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).containsSequence("kill-window", "-t", "@4"));
    }

    @Test
    void opensTheViewerWhereATaskWindowIsOpenedAndNoClientIsAttached() {
        Processes processes = mock(Processes.class);
        TerminalDriver terminal = mock(TerminalDriver.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-clients")))).thenReturn(new Processes.Result(0, "", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                terminal, mock(AgentRuntime.class));

        host.openTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"), false, "");

        verify(terminal).openViewer(eq("jagt"), eq("agents"), any());
    }

    @Test
    void revivesTheTaskInAWindowRunningTheRuntimesReviveCommand() {
        Processes processes = mock(Processes.class);
        AgentRuntime agent = mock(AgentRuntime.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        when(agent.reviveCommand(Path.of("/wt/ABC-1"))).thenReturn("agent --continue");
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), agent);

        host.reviveTaskWindow("jagt", "agents", "ABC-1", null, Path.of("/wt/ABC-1"));

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command -> {
            assertThat(command).containsSequence("new-window", "-P");
            assertThat(command.getLast()).contains("agent --continue");
        });
    }

    @Test
    void createsTheSessionForTheFeedWhereNoneExistsYet() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("has-session")))).thenReturn(new Processes.Result(1, "", "no session"));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/master.log"));

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command).containsSequence("new-session", "-d", "-s", "jagt"));
    }

    @Test
    void tailsTheFeedInAWindowOfItsOwnWhereNoneIsOpen() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/it's.log"));

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).anySatisfy(command ->
                assertThat(command.getLast()).startsWith("tail -n 200 -F '/feeds/it'\\''s.log'"));
    }

    @Test
    void opensNoSecondFeedWindowWhereOneIsOpen() {
        Processes processes = mock(Processes.class);
        when(processes.run(isNull(), any(Duration.class), anyList())).thenReturn(new Processes.Result(0, "@9", ""));
        when(processes.run(isNull(), any(Duration.class), argThat((List<String> command) ->
                command.contains("list-windows")))).thenReturn(new Processes.Result(0,
                "@5 100 1700000000 master-feed\n", ""));
        OrchestratorProperties properties = OrchestratorProperties.defaults();
        TmuxSessionHost host = new TmuxSessionHost(processes, properties, new OrchestratorPaths(properties),
                mock(TerminalDriver.class), mock(AgentRuntime.class));

        host.keepFeedWindow("jagt", "master-feed", Path.of("/feeds/master.log"));

        ArgumentCaptor<List<String>> commands = ArgumentCaptor.captor();
        verify(processes, atLeastOnce()).run(isNull(), any(Duration.class), commands.capture());
        assertThat(commands.getAllValues()).noneMatch(command -> command.contains("new-window"));
    }
}
