package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import java.time.Clock;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.master.MasterBrake;
import dev.jagt.orchestrator.task.PlanGate;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.TerminalDriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentSessionsTest {

    @TempDir
    Path root;

    private StateService state;
    private ConfigService config;
    private final SessionHost tmux = mock(SessionHost.class);
    private final TerminalDriver terminal = mock(TerminalDriver.class);
    private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

    @BeforeEach
    void aReadableConfigOverAnEmptyStateFile() throws Exception {
        Path configFile = Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n");
        OrchestratorProperties properties = OrchestratorProperties.defaults().withRoot(root.toString())
                .withStateFile(root.resolve("state.json").toString()).withConfigFile(configFile.toString());
        OrchestratorPaths paths = new OrchestratorPaths(properties);
        state = new StateService(new JsonMapper(), paths);
        config = new ConfigService(paths, agentRuntime, new ActStops(new MasterBrake(state, paths), new DailyStarts(paths, Clock.systemDefaultZone())));
    }

    @Test
    void closesTheTerminalWindowOfTheTaskItWasGiven() {
        state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt", "TEST-1")).thenReturn(1);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.closeTaskTab("TEST-1")).contains("Closed 1 tab(s) for TEST-1");
    }

    @Test
    void readsATasksScreenAsPlainTextWithoutItsColourCodes() {
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(java.util.Optional.of("\u001B[31m✗ two tests fail\u001B[0m\n\n"));
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.screen("ABC-1")).contains("✗ two tests fail");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u001B[>4;1m✗ two tests fail\u001B[0m", "\u001B(B✗ two tests fail",
            "\u001B7✗ two tests fail\u001B8"})
    void stripsEveryTerminalControlSequenceFromAScreen(String shown) {
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(java.util.Optional.of(shown));
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.screen("ABC-1")).contains("✗ two tests fail");
    }

    @Test
    void saysAScreenThatCouldNotBeReadRatherThanThatNoWindowHoldsIt() {
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).alias("a1").build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.screenOf("jagt", "ABC-1")).thenThrow(new IllegalStateException("tmux capture-pane failed (exit 1)"));
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThatThrownBy(() -> sessions.screen("ABC-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givesEachTaskItsOwnSessionWhenViewModeIsTabPerTask() throws Exception {
        state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
        Files.writeString(root.resolve("jagt.yml"),
                "orchestrator:\n  projects: {}\n  viewer:\n    tmuxSession: jagt\n    viewMode: tab-per-task\n");
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt-TEST-1", "TEST-1")).thenReturn(1);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.closeTaskTab("TEST-1");

        verify(tmux).killTaskWindows("jagt-TEST-1", "TEST-1");
    }

    @Test
    void refusesToCloseTheTabOfATaskNobodyOwns() {
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThatThrownBy(() -> sessions.closeTaskTab("ABC-9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ABC-9");
    }

    @ParameterizedTest
    @CsvSource({
            "WINDOW, and raised the agents window",
            "UNREACHABLE_TAB, a tab this terminal cannot select",
            "NOT_RUNNING, no agents viewer is open"
    })
    void saysWhatTheTerminalCouldActuallyDoAboutTheViewer(TerminalDriver.Revealed revealed, String expected) {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(terminal.reveal("jagt")).thenReturn(revealed);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.focusTask("ABC-1")).contains(expected);
    }

    @Test
    void nudgesAnAgentThatIsAlreadyRunningWhenNewInstructionsArrive() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.writeTaskContext("ABC-1", "new instructions")).contains("nudged");
    }

    @Test
    void typesNoNudgeIntoADraftTheHumanIsComposing() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"));
        when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        String answer = sessions.writeTaskContext("ABC-1", "new instructions");

        assertThat(answer).contains("the human is typing in the agent's window");
        verify(tmux, after(1500).never()).nudgeTaskWindow(anyString(), anyString(), anyString());
    }

    @Test
    void nudgesOnceTheHumanHasSentTheirDraft() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"), Optional.of("> "));
        when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.writeTaskContext("ABC-1", "new instructions");

        verify(tmux, timeout(5000)).nudgeTaskWindow("jagt", "ABC-1", AgentSessions.NUDGE);
    }

    @Test
    void typesAHumansLineIntoTheRunningSessionWithoutTouchingTheRoundBriefOnDisk() throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        Files.writeString(worktree.resolve("task_context.md"), "Review round for http://mr/1.");
        state.putTask("ABC-1", TaskState.builder("demo", worktree.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow("jagt", "ABC-1", "no, answer 2 differently")).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.say("ABC-1", "no, answer 2 differently");

        assertThat(Files.readString(worktree.resolve("task_context.md")))
                .isEqualTo("Review round for http://mr/1.");
    }

    @Test
    void refusesALineForATaskWhoseAgentSessionIsGoneRatherThanRespawningOverIt() {
        state.putTask("ABC-1", TaskState.builder("demo", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.DEAD_SHELL);
        when(agentRuntime.displayName()).thenReturn("Claude");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThatThrownBy(() -> sessions.say("ABC-1", "no, answer 2 differently"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Claude session is running for ABC-1");
    }

    @Test
    void startsAFreshSessionForNewInstructionsWhenTheConversationIdledPastItsCacheLifetime() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(agentRuntime.pastContinuing(root)).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        String answer = sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

        assertThat(answer).isEqualTo("Instructions written to task_context.md; a fresh session was started to read"
                + " them with task_notes.md.");
        verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
        verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
    }

    @Test
    void startsAFreshSessionWhenTheDraftItWaitedOnOutlastedTheCacheLifetime() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"), Optional.of("> "));
        when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);
        when(agentRuntime.pastContinuing(root)).thenReturn(false, true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

        verify(tmux, timeout(5000)).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
        verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
    }

    @Test
    void startsAFreshSessionForWorkOnAHandedBackRoundThoughTheConversationIsStillCached() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.relayFreshIfChanged("ABC-1", "Review round for http://mr/1.");

        verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
        verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
    }

    @Test
    void relaysAHandedBackRoundWhoseBriefTheWorktreeDoesNotHold() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        boolean relayed = sessions.relayFreshIfChanged("ABC-1", "Review round for http://mr/1.");

        assertThat(relayed).isTrue();
    }

    @Test
    void leavesAHandedBackRoundAloneWhenItsBriefIsAlreadyInTheWorktree() throws Exception {
        Files.writeString(root.resolve("task_context.md"), "Review round for http://mr/1.");
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.relayFreshIfChanged("ABC-1", "Review round for http://mr/1.")).isFalse();
        verifyNoInteractions(tmux);
    }

    @ParameterizedTest
    @CsvSource(value = {"auto,''", "plan,'; it plans first, and the plan is approved on the board with approve-plan'"})
    void namesTheSessionAndTheModeOfTheTabItStarted(String mode, String suffix) {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(agentRuntime.displayName()).thenReturn("Claude");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        String answer = sessions.openTaskTab("ABC-1", mode);

        assertThat(answer).isEqualTo("New Claude session started for ABC-1 in tab 'ABC-1' of session 'jagt' (worktree "
                + root + ")" + suffix);
    }

    @Test
    void reopeningInPlanModeClosesThePlanGate() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.openTaskTab("ABC-1", "plan");

        assertThat(state.task("ABC-1").orElseThrow().planGate()).isEqualTo(PlanGate.REQUIRED);
    }

    @Test
    void confirmsALineWasSaidToTheRunningAgent() {
        state.putTask("ABC-1", TaskState.builder("demo", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow("jagt", "ABC-1", "answer 2 differently")).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.say("ABC-1", "answer 2 differently")).isEqualTo("Said to the agent.");
    }

    @Test
    void reportsAnAgentLiveOnlyWhileItsWindowRunsTheAgent() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.DEAD_SHELL);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.agentLive("ABC-1")).isFalse();
    }

    @Test
    void reportsAnAgentLiveWhereItsWindowRunsTheAgent() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.agentLive("ABC-1")).isTrue();
    }

    @Test
    void startsTheFreshSessionOnTheModelTheNotesName() throws Exception {
        Files.writeString(root.resolve("task_notes.md"), "kept the retry\nnext-model: sonnet\n");
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(agentRuntime.choosesModel()).thenReturn(true);
        when(agentRuntime.models()).thenReturn(List.of("opus", "sonnet"));
        when(agentRuntime.displayName()).thenReturn("Claude");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.openTaskTab("ABC-1", "auto");

        verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "sonnet");
    }

    @Test
    void startsTheFreshSessionOnTheDefaultModelWhenTheRuntimeCannotChoose() throws Exception {
        Files.writeString(root.resolve("task_notes.md"), "next-model: sonnet\n");
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(agentRuntime.displayName()).thenReturn("Codex");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.openTaskTab("ABC-1", "auto");

        verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false, "");
    }

    @Test
    void nudgesTheRunningConversationWhenItIsStillWithinItsCacheLifetime() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);
        when(agentRuntime.pastContinuing(root)).thenReturn(false);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.writeTaskContext("ABC-1", "Review round for http://mr/1.");

        verify(tmux, never()).openTaskWindow(anyString(), anyString(), anyString(), any(), any(), eq(false), anyString());
    }

    @Test
    void reEntersTheSessionOfATaskWhoseWindowIsGoneRatherThanDroppingTheRelay() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.writeTaskContext("ABC-1", "new instructions")).contains("re-entered");
        verify(tmux).reviveTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any());
    }

    @Test
    void reEntersWhatTheDeadSessionKnewWhenFocusingATaskTheMachineRestartedUnder() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);
        when(terminal.reveal("jagt")).thenReturn(TerminalDriver.Revealed.WINDOW);
        when(agentRuntime.displayName()).thenReturn("Claude");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.focusTask("ABC-1")).contains("re-entered its Claude session");
        verify(tmux).reviveTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any());
    }

    @Test
    void startsAFreshSessionWhenAHumanRestartsTheAgentInsteadOfReEnteringTheOldOne() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(agentRuntime.displayName()).thenReturn("Claude");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.openTaskTab("ABC-1", "auto")).contains("New Claude session started");
        verify(tmux).openTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any(), eq(false), anyString());
        verify(tmux, never()).reviveTaskWindow(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void replacesTheRelayForANewRoundOfWork() throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        Files.writeString(worktree.resolve("task_context.md"), "STALE: last round");
        state.putTask("ABC-1", TaskState.builder("demo", worktree.toString(), TaskStatus.CI_POLLING).build());
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.writeTaskContext("ABC-1", "NEW ROUND: fix the pipeline");

        assertThat(Files.readString(worktree.resolve("task_context.md")))
                .isEqualTo("NEW ROUND: fix the pipeline").doesNotContain("STALE");
    }

    @Test
    void leavesTheAgentAloneWhenTheRoundIsTheOneItWasAlreadyHanded() throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        Files.writeString(worktree.resolve("task_context.md"), "Review round for http://mr/1.\nComment: rename x");
        state.putTask("ABC-1", TaskState.builder("demo", worktree.toString(), TaskStatus.CI_POLLING).build());
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.relayIfChanged("ABC-1", "Review round for http://mr/1.\nComment: rename x"))
                .isFalse();
        verifyNoInteractions(tmux);
    }

    @Test
    void relaysARoundThatDiffersFromTheOneTheAgentWasHanded() throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        Files.writeString(worktree.resolve("task_context.md"), "Review round for http://mr/1.\nComment: rename x");
        state.putTask("ABC-1", TaskState.builder("demo", worktree.toString(), TaskStatus.CI_POLLING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.relayIfChanged("ABC-1", "Review round for http://mr/1.\nComment: drop the cache"))
                .isTrue();
        assertThat(Files.readString(worktree.resolve("task_context.md"))).contains("drop the cache");
    }

    @Test
    void keepsTheAgentsViewerOpenAfterTheLastTaskByDefault() throws Exception {
        Files.writeString(root.resolve("jagt.yml"), "orchestrator:\n  projects: {}\n  viewer:\n    tmuxSession: jagt\n");
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.closeViewerIfNoTasksLeft()).isFalse();
        verifyNoInteractions(terminal);
    }

    @Test
    void closesTheAgentsViewerAfterTheLastTaskWhenReservingItIsTurnedOff() throws Exception {
        Files.writeString(root.resolve("jagt.yml"),
                "orchestrator:\n  projects: {}\n  viewer:\n    tmuxSession: jagt\n    keepViewer: false\n");
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.closeViewerIfNoTasksLeft()).isTrue();
        verify(terminal).closeViewerWindow("jagt");
    }

    @Test
    void saysTheHumanIsComposingWhereTheWindowHoldsADraft() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"));
        when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.composing("ABC-1")).isTrue();
    }

    @Test
    void saysTheHumanIsNotComposingWhereTheWindowHoldsNoDraft() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> "));
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        assertThat(sessions.composing("ABC-1")).isFalse();
    }

    @Test
    void nudgesAtOnceWhereTheScreenCannotBeReadSinceNoDraftIsKnown() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.screenOf("jagt", "ABC-1")).thenThrow(new IllegalStateException("capture-pane failed"));
        when(tmux.nudgeTaskWindow("jagt", "ABC-1", AgentSessions.NUDGE)).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        String answer = sessions.writeTaskContext("ABC-1", "new instructions");

        assertThat(answer).contains("the agent was nudged");
    }

    @Test
    void nudgesNothingOnceTheWindowIsGoneWhileTheHumanWasTyping() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING,
                SessionHost.WindowState.AGENT_RUNNING, SessionHost.WindowState.MISSING);
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"), Optional.of("> "));
        when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);
        AgentSessions sessions = new AgentSessions(config, state, tmux, terminal, agentRuntime);

        sessions.writeTaskContext("ABC-1", "new instructions");

        verify(tmux, after(2500).never()).nudgeTaskWindow(anyString(), anyString(), anyString());
    }
}
