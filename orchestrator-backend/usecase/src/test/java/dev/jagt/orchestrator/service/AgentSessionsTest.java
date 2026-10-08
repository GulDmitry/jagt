package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.port.TerminalDriver;
import dev.jagt.orchestrator.service.ConfigService.ConfigFile.ViewerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    void aReadableConfigOverAnEmptyStateFile() {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    private AgentSessions sessions() {
        return new AgentSessions(config, state, tmux, terminal, agentRuntime);
    }

    @Test
    void closesTheTerminalWindowOfTheTaskItWasGiven() {
        state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt", "TEST-1")).thenReturn(1);

        assertThat(sessions().closeTaskTab("TEST-1")).contains("Closed 1 tab(s) for TEST-1");
    }

    @Test
    void givesEachTaskItsOwnSessionWhenViewModeIsTabPerTask() {
        state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ViewerConfig.defaults().withTmuxSession("jagt").withViewMode("tab-per-task")));
        when(tmux.sessionName("jagt")).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt-TEST-1", "TEST-1")).thenReturn(1);

        sessions().closeTaskTab("TEST-1");

        verify(tmux).killTaskWindows("jagt-TEST-1", "TEST-1");
    }

    @Test
    void namesTheSessionATasksWindowLivesIn() {
        state.putTask("ABC-1", TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");

        assertThat(sessions().sessionOf("ABC-1")).isEqualTo("jagt");
    }

    @Test
    void refusesToNameASessionForATaskNobodyOwns() {
        assertThatThrownBy(() -> sessions().sessionOf("ABC-9"))
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

        assertThat(sessions().focusTask("ABC-1")).contains(expected);
    }

    @Test
    void nudgesAnAgentThatIsAlreadyRunningWhenNewInstructionsArrive() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);

        assertThat(sessions().writeTaskContext("ABC-1", "new instructions")).contains("nudged");
    }

    @Test
    void typesNoNudgeIntoADraftTheHumanIsComposing() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"));
        when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);

        sessions().writeTaskContext("ABC-1", "new instructions");

        verify(tmux, after(1500).never()).nudgeTaskWindow(anyString(), anyString(), anyString());
    }

    @Test
    void nudgesOnceTheHumanHasSentTheirDraft() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.screenOf("jagt", "ABC-1")).thenReturn(Optional.of("> where is the merge re"), Optional.of("> "));
        when(agentRuntime.holdsDraft("> where is the merge re")).thenReturn(true);

        sessions().writeTaskContext("ABC-1", "new instructions");

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

        sessions().say("ABC-1", "no, answer 2 differently");

        assertThat(Files.readString(worktree.resolve("task_context.md")))
                .isEqualTo("Review round for http://mr/1.");
    }

    @Test
    void refusesALineForATaskWhoseAgentSessionIsGoneRatherThanRespawningOverIt() {
        state.putTask("ABC-1", TaskState.builder("demo", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.DEAD_SHELL);
        when(agentRuntime.displayName()).thenReturn("Claude");

        assertThatThrownBy(() -> sessions().say("ABC-1", "no, answer 2 differently"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Claude session is running for ABC-1");
    }

    @Test
    void startsAFreshSessionForNewInstructionsWhenTheConversationIdledPastItsCacheLifetime() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(agentRuntime.continuesWithin()).thenReturn(Optional.of(Duration.ofHours(1)));
        when(agentRuntime.lastSessionActivity(root))
                .thenReturn(OptionalLong.of(1_700_000_000_000L));

        sessions().writeTaskContext("ABC-1", "Review round for http://mr/1.");

        verify(tmux).openTaskWindow("jagt", "jagt", "ABC-1", null, root, false);
        verify(tmux, never()).nudgeTaskWindow(anyString(), anyString(), anyString());
    }

    @Test
    void nudgesTheRunningConversationWhenItIsStillWithinItsCacheLifetime() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.REVIEW_PENDING).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);
        when(agentRuntime.continuesWithin()).thenReturn(Optional.of(Duration.ofHours(1)));
        when(agentRuntime.lastSessionActivity(root))
                .thenReturn(OptionalLong.of(System.currentTimeMillis() - Duration.ofMinutes(10).toMillis()));

        sessions().writeTaskContext("ABC-1", "Review round for http://mr/1.");

        verify(tmux, never()).openTaskWindow(anyString(), anyString(), anyString(), any(), any(), eq(false));
    }

    @Test
    void reEntersTheSessionOfATaskWhoseWindowIsGoneRatherThanDroppingTheRelay() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);

        assertThat(sessions().writeTaskContext("ABC-1", "new instructions")).contains("re-entered");
        verify(tmux).reviveTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any());
    }

    @Test
    void reEntersWhatTheDeadSessionKnewWhenFocusingATaskTheMachineRestartedUnder() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);
        when(terminal.reveal("jagt")).thenReturn(TerminalDriver.Revealed.WINDOW);
        when(agentRuntime.displayName()).thenReturn("Claude");

        assertThat(sessions().focusTask("ABC-1")).contains("re-entered its Claude session");
        verify(tmux).reviveTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any());
    }

    @Test
    void startsAFreshSessionWhenAHumanRestartsTheAgentInsteadOfReEnteringTheOldOne() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(agentRuntime.displayName()).thenReturn("Claude");

        assertThat(sessions().openTaskTab("ABC-1", "auto")).contains("New Claude session started");
        verify(tmux).openTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any(), eq(false));
        verify(tmux, never()).reviveTaskWindow(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void replacesTheRelayForANewRoundOfWork() throws Exception {
        Path worktree = worktreeWithRelay("STALE: last round");

        sessions().writeTaskContext("ABC-1", "NEW ROUND: fix the pipeline");

        assertThat(Files.readString(worktree.resolve("task_context.md")))
                .isEqualTo("NEW ROUND: fix the pipeline").doesNotContain("STALE");
    }

    @Test
    void leavesTheAgentAloneWhenTheRoundIsTheOneItWasAlreadyHanded() throws Exception {
        worktreeWithRelay("Review round for http://mr/1.\nComment: rename x");

        assertThat(sessions().relayIfChanged("ABC-1", "Review round for http://mr/1.\nComment: rename x"))
                .isFalse();
        verifyNoInteractions(tmux);
    }

    @Test
    void relaysARoundThatDiffersFromTheOneTheAgentWasHanded() throws Exception {
        Path worktree = worktreeWithRelay("Review round for http://mr/1.\nComment: rename x");
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);

        assertThat(sessions().relayIfChanged("ABC-1", "Review round for http://mr/1.\nComment: drop the cache"))
                .isTrue();
        assertThat(Files.readString(worktree.resolve("task_context.md"))).contains("drop the cache");
    }

    private Path worktreeWithRelay(String existingContent) throws Exception {
        Path worktree = Files.createDirectories(root.resolve("ABC-1-demo"));
        Files.writeString(worktree.resolve("task_context.md"), existingContent);
        state.putTask("ABC-1", TaskState.builder("demo", worktree.toString(), TaskStatus.CI_POLLING).build());
        return worktree;
    }

    @Test
    void keepsTheAgentsViewerOpenAfterTheLastTaskByDefault() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ConfigService.ConfigFile.ViewerConfig.defaults().withTmuxSession("jagt")));
        when(tmux.sessionName("jagt")).thenReturn("jagt");

        assertThat(sessions().closeViewerIfNoTasksLeft()).isFalse();
        verifyNoInteractions(terminal);
    }

    @Test
    void closesTheAgentsViewerAfterTheLastTaskWhenReservingItIsTurnedOff() {
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults()
                .withViewer(ConfigService.ConfigFile.ViewerConfig.defaults().withTmuxSession("jagt")
                        .withKeepViewer(false)));
        when(tmux.sessionName("jagt")).thenReturn("jagt");

        assertThat(sessions().closeViewerIfNoTasksLeft()).isTrue();
        verify(terminal).closeViewerWindow("jagt");
    }
}
