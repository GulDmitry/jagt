package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.AgentRuntime;

import dev.jagt.orchestrator.config.ClaudeProperties;

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

    @BeforeEach
    void setUp() {
        OrchestratorProperties properties = OrchestratorProperties.defaults()
                .withRoot(root.toString()).withStateFile(root.resolve("state.json").toString());
        state = new StateService(new JsonMapper(), new OrchestratorPaths(properties));
        config = mock(ConfigService.class);
        when(config.load()).thenReturn(ConfigService.ConfigFile.defaults());
    }

    private final AgentRuntime agentRuntime = mock(AgentRuntime.class);

    private AgentSessions sessions() {
        return new AgentSessions(config, state, tmux, terminal, agentRuntime);
    }

    @Test
    void closesTaskWindowWhenCalledWithItsTaskId() {
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

    @Test
    void nudgesRunningAgentWhenTaskContextIsUpdated() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.AGENT_RUNNING);
        when(tmux.nudgeTaskWindow(eq("jagt"), eq("ABC-1"), anyString())).thenReturn(true);

        assertThat(sessions().writeTaskContext("ABC-1", "new instructions")).contains("nudged");
    }

    @Test
    void respawnsADownSessionWhenWriteTaskContextTargetsIt() {
        state.putTask("ABC-1", TaskState.builder("proj", root.toString(), TaskStatus.IN_PROGRESS).build());
        when(tmux.sessionName(null)).thenReturn("jagt");
        when(tmux.taskWindowState("jagt", "ABC-1")).thenReturn(SessionHost.WindowState.MISSING);

        assertThat(sessions().writeTaskContext("ABC-1", "new instructions")).contains("respawned");
        verify(tmux).openTaskWindow(anyString(), anyString(), eq("ABC-1"), any(), any(), eq(false));
    }

    @Test
    void addsToAnUnreadRelayInsteadOfWipingIt() throws Exception {
        // Two flows relay to one file: a sweep's brief with unresolved comments, and ship's "post your drafted
        // replies". Truncating meant the agent never saw whichever arrived first — the review looked clean.
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
