package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.flow.TaskStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The tmux/relay half of what used to live in the OrchestratorTools facade. Its whole setup is four
 * collaborators and no editor, notifier, git or provisioning — the point of extracting it.
 */
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
    void closesTheTmuxWindowOfTheTaskItWasGiven() {
        state.putTask("TEST-1", TaskState.builder("proj", "/wt", TaskStatus.DONE).alias("t1").build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.killTaskWindows("jagt", "TEST-1")).thenReturn(1);

        assertThat(sessions().closeTaskTab("TEST-1")).contains("Closed 1 tmux window(s) for TEST-1");
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

    /**
     * A boolean made the caller guess WHY nothing came forward, and the one sentence it guessed was written for
     * a terminal whose viewer is a tab — so a terminal that simply was not running said the same thing.
     */
    @ParameterizedTest
    @CsvSource({
            "WINDOW, and raised the agents window",
            "UNREACHABLE_TAB, no API to select one",
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
    void respawnsATaskWhoseSessionIsGoneRatherThanDroppingTheRelay() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);

        assertThat(sessions().writeTaskContext("ABC-1", "new instructions")).contains("respawned");
        verify(tmux).openTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any(), eq(false));
    }

    /**
     * Two flows relay into one file — a sweep's brief and ship's "post your drafted replies" — so truncating
     * means the agent never sees whichever arrived first and the review reads as clean.
     */
    @Test
    void addsToAnUnreadRelayInsteadOfWipingIt() throws Exception {
        Path worktree = worktreeWithRelay("BRIEF: four unresolved comments");

        sessions().appendTaskContext("ABC-1", "ALSO: post your drafted replies");

        assertThat(Files.readString(worktree.resolve("task_context.md")))
                .contains("BRIEF: four unresolved comments", "ALSO: post your drafted replies");
    }

    @Test
    void replacesTheRelayForANewRoundOfWork() throws Exception {
        Path worktree = worktreeWithRelay("STALE: last round");

        sessions().writeTaskContext("ABC-1", "NEW ROUND: fix the pipeline");

        assertThat(Files.readString(worktree.resolve("task_context.md")))
                .isEqualTo("NEW ROUND: fix the pipeline").doesNotContain("STALE");
    }

    /**
     * The poller reads the same round every interval while the request stands still, and a relay NUDGES the
     * session — so an unchanged brief would interrupt the agent to re-decide comments it has already answered.
     */
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
